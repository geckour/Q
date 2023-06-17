package com.geckour.q.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PointF
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.SeekBar
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import com.dropbox.core.DbxHost
import com.dropbox.core.android.Auth
import com.geckour.q.BuildConfig
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.ActivityMainBinding
import com.geckour.q.databinding.DialogSleepBinding
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.RequestedTransition
import com.geckour.q.ui.easteregg.EasterEggFragment
import com.geckour.q.ui.equalizer.EqualizerFragment
import com.geckour.q.ui.library.album.AlbumListFragment
import com.geckour.q.ui.library.artist.ArtistListFragment
import com.geckour.q.ui.library.genre.GenreListFragment
import com.geckour.q.ui.library.track.TrackListFragment
import com.geckour.q.ui.pay.PaymentFragment
import com.geckour.q.ui.setting.SettingActivity
import com.geckour.q.ui.sheet.BottomSheetFragment
import com.geckour.q.util.OrientedClassType
import com.geckour.q.util.dbxRequestConfig
import com.geckour.q.util.dropboxCredential
import com.geckour.q.util.getTimeString
import com.geckour.q.util.isNightMode
import com.geckour.q.util.preferScreen
import com.geckour.q.util.showFileMetadataUpdateDialog
import com.geckour.q.util.sleepTimerTime
import com.geckour.q.util.sleepTimerTolerance
import com.geckour.q.util.toDomainTrack
import com.geckour.q.util.toNightModeInt
import com.geckour.q.util.updateFileMetadata
import com.geckour.q.worker.DropboxMediaRetrieveWorker
import com.geckour.q.worker.KEY_SYNCING_FINISHED
import com.geckour.q.worker.KEY_SYNCING_PROGRESS_DENOMINATOR
import com.geckour.q.worker.KEY_SYNCING_PROGRESS_NUMERATOR
import com.geckour.q.worker.KEY_SYNCING_PROGRESS_PATH
import com.geckour.q.worker.KEY_SYNCING_REMAINING
import com.geckour.q.worker.LocalMediaRetrieveWorker
import com.geckour.q.worker.MEDIA_RETRIEVE_WORKER_NAME
import com.geckour.q.worker.SleepTimerWorker
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import permissions.dispatcher.ktx.constructPermissionsRequest
import timber.log.Timber
import java.io.File
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    companion object {

        private const val STATE_KEY_REQUESTED_TRANSACTION = "state_key_requested_transaction"

        fun createIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
    }

    private val viewModel by viewModel<MainViewModel>()

    internal lateinit var binding: ActivityMainBinding
    private val sharedPreferences by inject<SharedPreferences>()

    private lateinit var drawerToggle: ActionBarDrawerToggle

    private val searchListAdapter: SearchListAdapter = SearchListAdapter(
        onNewQueue = { actionType, track ->
            viewModel.onNewQueue(listOf(track), actionType, OrientedClassType.TRACK)
        },
        onEditMetadata = { track ->
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.onLoadStateChanged(true)
                    val tracks = get<DB>().trackDao().get(track.id)?.let { listOf(it) }.orEmpty()
                    viewModel.onLoadStateChanged(false)

                    this@MainActivity.showFileMetadataUpdateDialog(tracks) { binding ->
                        lifecycleScope.launch {
                            repeatOnLifecycle(Lifecycle.State.STARTED) {
                                viewModel.onLoadStateChanged(true)
                                binding.updateFileMetadata(this@MainActivity, get(), tracks)
                                viewModel.onLoadStateChanged(false)
                            }
                        }
                    }
                }
            }
        },
        onClickArtist = { artist ->
            viewModel.selectedArtist.value = artist
        },
        onClickAlbum = { album ->
            viewModel.selectedAlbum.value = album
        },
        onClickGenre = { genre ->
            viewModel.selectedGenre.value = genre
        }
    )

    private var requestedTransition: RequestedTransition? = null
    private var paused = true

    private val onNavigationItemSelected: (MenuItem) -> Boolean = {
        when (it.itemId) {
            R.id.nav_artist -> ArtistListFragment.newInstance()
            R.id.nav_album -> AlbumListFragment.newInstance()
            R.id.nav_track -> TrackListFragment.newInstance()
            R.id.nav_genre -> GenreListFragment.newInstance()
            R.id.nav_setting -> {
                startActivity(SettingActivity.createIntent(this))
                null
            }

            R.id.nav_equalizer -> EqualizerFragment.newInstance()
            R.id.nav_sync -> {
                FirebaseAnalytics.getInstance(this)
                    .logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, Bundle().apply {
                        putString(
                            FirebaseAnalytics.Param.ITEM_NAME, "Invoked force sync"
                        )
                    })

                retrieveMedia(false)
                null
            }

            R.id.nav_dropbox_sync -> {
                val credential = sharedPreferences.dropboxCredential
                if (credential.isNullOrBlank()) {
                    viewModel.isDropboxAuthOngoing = true
                    Auth.startOAuth2PKCE(
                        this,
                        BuildConfig.DROPBOX_APP_KEY,
                        dbxRequestConfig,
                        DbxHost.DEFAULT
                    )
                } else {
                    viewModel.showDropboxFolderChooser()
                }
                null
            }

            R.id.nav_pay -> PaymentFragment.newInstance()
            else -> null
        }?.let {
            supportFragmentManager.commit {
                if (binding.contentMain.childCount == 0) add(R.id.content_main, it)
                else {
                    replace(R.id.content_main, it)
                    addToBackStack(null)
                }
            }
        }
        binding.drawerLayout.post { binding.drawerLayout.closeDrawers() }
        true
    }

    private var dropboxChooserDialog: DropboxChooserDialog? = null

    private var pointAtStart: PointF? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        binding.contentSearch.recyclerView.adapter = searchListAdapter

        observeEvents()

        setSupportActionBar(binding.toolbar)
        setupDrawer()

        if (savedInstanceState == null) {
            viewModel.checkDBIsEmpty { retrieveMedia(false) }
            retrieveMedia(true)

            val navId = PreferenceManager.getDefaultSharedPreferences(this).preferScreen.value.navId
            onNavigationItemSelected(binding.navigationView.menu.findItem(navId))
        } else if (savedInstanceState.containsKey(STATE_KEY_REQUESTED_TRANSACTION)) {
            requestedTransition =
                if (Build.VERSION.SDK_INT < 33) {
                    savedInstanceState.getParcelable(STATE_KEY_REQUESTED_TRANSACTION) as RequestedTransition?
                } else {
                    savedInstanceState.getParcelable(
                        STATE_KEY_REQUESTED_TRANSACTION,
                        RequestedTransition::class.java
                    )
                }
        }

        supportFragmentManager.commit {
            add(R.id.bottom_sheet, BottomSheetFragment.newInstance())
        }
    }

    override fun onResume() {
        super.onResume()

        delegate.localNightMode = sharedPreferences.isNightMode.toNightModeInt

        paused = false

        tryTransaction()

        if (viewModel.isDropboxAuthOngoing) {
            viewModel.isDropboxAuthOngoing = false
            onAuthDropboxCompleted()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return when (ev.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                pointAtStart = PointF(ev.x, ev.y)
                super.dispatchTouchEvent(ev)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val startPoint = pointAtStart ?: return super.dispatchTouchEvent(ev)
                val distanceX = startPoint.x - ev.x
                val distanceY = startPoint.y - ev.y
                return if (binding.drawerLayout.isOpen.not() &&
                    distanceX < 0 && abs(distanceX) / abs(distanceY) > 1.2
                ) {
                    super.dispatchTouchEvent(ev.apply { action = MotionEvent.ACTION_CANCEL })
                    binding.drawerLayout.openDrawer(GravityCompat.START)
                    true
                } else {
                    pointAtStart = null
                    super.dispatchTouchEvent(ev)
                }
            }

            else -> super.dispatchTouchEvent(ev)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        requestedTransition?.apply {
            outState.putParcelable(STATE_KEY_REQUESTED_TRANSACTION, this)
        }
    }

    override fun onPause() {
        paused = true

        super.onPause()
    }

    override fun onBackPressed() {
        when {
            viewModel.isSearchViewOpened -> {
                viewModel.searchQueryListener.reset()
                binding.contentSearch.root.visibility = View.GONE
            }

            binding.drawerLayout.isDrawerOpen(binding.navigationView) -> {
                binding.drawerLayout.closeDrawer(binding.navigationView)
            }

            else -> super.onBackPressed()
        }
    }

    private fun onCancelSync() {
        setLockingIndicator(false, null)
        viewModel.forceLoad.postValue(Unit)
    }

    private fun retrieveMedia(onlyAdded: Boolean) {
        if (Build.VERSION.SDK_INT < 33) {
            constructPermissionsRequest(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                onPermissionDenied = ::onReadExternalStorageDenied
            ) {
                enqueueLocalRetrieveWorker(onlyAdded)
            }.launch()
        } else {
            enqueueLocalRetrieveWorker(onlyAdded)
        }
    }

    private fun retrieveDropboxMedia(rootPath: String) {
        if (Build.VERSION.SDK_INT < 33) {
            constructPermissionsRequest(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                onPermissionDenied = ::onReadExternalStorageDenied
            ) {
                enqueueDropboxRetrieveWorker(rootPath)
            }.launch()
        } else {
            enqueueDropboxRetrieveWorker(rootPath)
        }
    }

    private fun enqueueLocalRetrieveWorker(onlyAdded: Boolean) {
        viewModel.workManager
            .beginUniqueWork(
                MEDIA_RETRIEVE_WORKER_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<LocalMediaRetrieveWorker>()
                    .setInputData(
                        Data.Builder()
                            .putBoolean(LocalMediaRetrieveWorker.KEY_ONLY_ADDED, onlyAdded)
                            .build()
                    )
                    .build()
            )
            .enqueue()
    }

    private fun enqueueDropboxRetrieveWorker(rootPath: String) {
        viewModel.workManager
            .beginUniqueWork(
                MEDIA_RETRIEVE_WORKER_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<DropboxMediaRetrieveWorker>()
                    .setInputData(
                        Data.Builder()
                            .putString(DropboxMediaRetrieveWorker.KEY_ROOT_PATH, rootPath)
                            .build()
                    )
                    .build()
            )
            .enqueue()
    }

    private fun onReadExternalStorageDenied() {
        retrieveMedia(false)
    }

    private fun observeEvents() {
        viewModel.currentFragmentId.observe(this) { navId ->
            navId ?: return@observe
            binding.navigationView.setCheckedItem(navId)
            val title = supportFragmentManager.fragments.firstOrNull {
                when (navId) {
                    R.id.nav_artist -> it is ArtistListFragment
                    R.id.nav_album -> it is AlbumListFragment
                    R.id.nav_track -> it is TrackListFragment
                    R.id.nav_genre -> it is GenreListFragment
                    else -> false
                }
            }?.tag ?: getString(
                when (navId) {
                    R.id.nav_artist -> R.string.nav_artist
                    R.id.nav_album -> R.string.nav_album
                    R.id.nav_track -> R.string.nav_track
                    R.id.nav_genre -> R.string.nav_genre
                    R.id.nav_equalizer -> R.string.nav_equalizer
                    R.id.nav_pay -> R.string.nav_pay
                    R.layout.fragment_easter_egg -> R.string.nav_fortune
                    else -> return@observe
                }
            )
            supportActionBar?.title = title
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loading.collectLatest {
                    setLockingIndicator(false, it)
                }
            }
        }

        viewModel.selectedArtist.observe(this) {
            it ?: return@observe
            requestedTransition = RequestedTransition(
                RequestedTransition.Tag.ARTIST, artist = it
            )
            tryTransaction()
        }

        viewModel.selectedAlbum.observe(this) {
            it ?: return@observe
            requestedTransition = RequestedTransition(RequestedTransition.Tag.ALBUM, album = it)
            tryTransaction()
        }

        viewModel.selectedGenre.observe(this) {
            it ?: return@observe
            requestedTransition = RequestedTransition(RequestedTransition.Tag.GENRE, genre = it)
            tryTransaction()
        }

        viewModel.trackToDelete.observe(this) {
            it ?: return@observe
            deleteFromDevice(it)
        }

        viewModel.searchItems.observe(this) {
            if (it.isEmpty()) {
                binding.contentSearch.root.visibility = View.GONE
                return@observe
            } else binding.contentSearch.root.visibility = View.VISIBLE

            searchListAdapter.submitList(it)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.dropboxItemList.collectLatest {
                    (dropboxChooserDialog ?: run {
                        DropboxChooserDialog(
                            this@MainActivity,
                            onClickItem = { metadata ->
                                viewModel.showDropboxFolderChooser(metadata)
                            },
                            onPrev = { metadata ->
                                viewModel.showDropboxFolderChooser(metadata)
                            },
                            onChoose = { path ->
                                retrieveDropboxMedia(path)
                            }
                        ).apply { dropboxChooserDialog = this }
                    }).show(it.first, it.second)
                }
            }
        }

        viewModel.mediaRetrieveWorkInfoList.observe(this) { workInfoList ->
            if (workInfoList.none { it.state == WorkInfo.State.RUNNING }) {
                onCancelSync()
                return@observe
            }

            workInfoList.forEach { workInfo ->
                workInfo.progress.also {
                    it.getInt(KEY_SYNCING_PROGRESS_NUMERATOR, -1).let { numerator ->
                        if (numerator < 0) return@let

                        val denominator = it.getInt(KEY_SYNCING_PROGRESS_DENOMINATOR, -1)
                        val path = it.getString(KEY_SYNCING_PROGRESS_PATH)
                        val remaining = it.getLong(KEY_SYNCING_REMAINING, -1)
                        setLockingIndicator(true, null)
                        binding.indicatorLocking.progressSync.text =
                            if (denominator < 0) null
                            else getString(R.string.progress_sync, numerator, denominator)
                        binding.indicatorLocking.progressPath.text = path
                        binding.indicatorLocking.remaining.text =
                            if (remaining > -1) getString(
                                R.string.remaining,
                                remaining.getTimeString()
                            )
                            else ""
                    }
                }
                if (workInfo.outputData.getBoolean(KEY_SYNCING_FINISHED, false)) {
                    onCancelSync()
                }
            }
        }
    }

    private fun setupDrawer() {
        drawerToggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar, R.string.drawer_open, R.string.drawer_close
        )
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        binding.navigationView.setNavigationItemSelectedListener(onNavigationItemSelected)
        binding.navigationView.getHeaderView(0)
            .findViewById<View>(R.id.drawer_head_icon)
            ?.setOnLongClickListener {
                requestedTransition = RequestedTransition(RequestedTransition.Tag.EASTER_EGG)
                tryTransaction()
                true
            }
    }

    private fun setLockingIndicator(syncing: Boolean, loading: Pair<Boolean, (() -> Unit)?>?) {
        when {
            syncing -> {
                indicateSync()
                toggleIndicateLock(true)
            }

            loading?.first == true -> {
                indicateLoad(loading.second)
                toggleIndicateLock(true)
            }

            else -> {
                toggleIndicateLock(false)
            }
        }
    }

    private fun indicateSync() {
        binding.indicatorLocking.descLocking.text = getString(R.string.syncing)
        binding.indicatorLocking.remaining.visibility = View.VISIBLE
        binding.indicatorLocking.progressSync.visibility = View.VISIBLE
        binding.indicatorLocking.progressPath.visibility = View.VISIBLE
        binding.indicatorLocking.buttonCancel.apply {
            setOnClickListener {
                viewModel.workManager
                    .cancelUniqueWork(MEDIA_RETRIEVE_WORKER_NAME)
            }
            visibility = View.VISIBLE
        }
    }

    private fun indicateLoad(onAbort: (() -> Unit)?) {
        binding.indicatorLocking.descLocking.text = getString(R.string.loading)
        binding.indicatorLocking.progressSync.visibility = View.GONE
        binding.indicatorLocking.progressPath.visibility = View.GONE
        onAbort?.let {
            binding.indicatorLocking.buttonCancel.apply {
                setOnClickListener { it() }
                visibility = View.VISIBLE
            }
        } ?: run {
            binding.indicatorLocking.buttonCancel.visibility = View.GONE
        }
    }

    private fun toggleIndicateLock(locking: Boolean) {
        binding.indicatorLocking.root.visibility = if (locking) View.VISIBLE else View.GONE
        binding.drawerLayout.setDrawerLockMode(
            if (locking) DrawerLayout.LOCK_MODE_LOCKED_CLOSED
            else DrawerLayout.LOCK_MODE_UNLOCKED
        )
    }

    private fun tryTransaction() {
        if (paused.not()) {
            requestedTransition?.apply {
                Timber.d("qgeck requested transaction: $requestedTransition")
                dismissSearch()
                when (this.tag) {
                    RequestedTransition.Tag.ARTIST -> {
                        if (artist != null) {
                            supportFragmentManager.commit {
                                replace(
                                    R.id.content_main,
                                    AlbumListFragment.newInstance(artist),
                                    artist.title
                                )
                                addToBackStack(null)
                            }
                        }
                    }

                    RequestedTransition.Tag.ALBUM -> {
                        if (album != null) {
                            supportFragmentManager.commit {
                                replace(
                                    R.id.content_main,
                                    TrackListFragment.newInstance(album),
                                    album.title
                                )
                                addToBackStack(null)
                            }
                        }
                    }

                    RequestedTransition.Tag.GENRE -> {
                        if (genre != null) {
                            supportFragmentManager.commit {
                                replace(
                                    R.id.content_main,
                                    TrackListFragment.newInstance(genre),
                                    genre.name
                                )
                                addToBackStack(null)
                            }
                        }
                    }

                    RequestedTransition.Tag.EASTER_EGG -> {
                        supportFragmentManager.commit {
                            replace(
                                R.id.content_main,
                                EasterEggFragment.newInstance()
                            )
                            addToBackStack(null)
                        }
                        binding.drawerLayout.closeDrawer(binding.navigationView)
                    }
                }
                requestedTransition = null
            }
        }
    }

    private fun dismissSearch() {
        binding.contentSearch.root.visibility = View.GONE
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(
            currentFocus?.windowToken, 0
        )
    }

    private fun deleteFromDevice(domainTrack: DomainTrack) {
        if (domainTrack.mediaId < 0) return

        constructPermissionsRequest(
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) {
            File(domainTrack.sourcePath).apply {
                if (this.exists()) {
                    viewModel.onRemoveQueueByTrack?.invoke(domainTrack)
                    this.delete()
                }
            }
            contentResolver.delete(
                MediaStore.Files.getContentUri("external"),
                "${MediaStore.Files.FileColumns.DATA}=?",
                arrayOf(domainTrack.sourcePath)
            )
        }.launch()
    }

    internal fun showSleepTimerDialog() {
        val binding = DialogSleepBinding.inflate(LayoutInflater.from(this)).apply {
            val cachedTimerValue = sharedPreferences.sleepTimerTime
            val cachedToleranceValue = sharedPreferences.sleepTimerTolerance
            timerValue = cachedTimerValue
            toleranceValue = cachedToleranceValue
            timerSlider.progress = cachedTimerValue / 5
            toleranceSlider.progress = cachedToleranceValue

            timerSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?, progress: Int, fromUser: Boolean
                ) {
                    val value = progress * 5
                    timerValue = value
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
            toleranceSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?, progress: Int, fromUser: Boolean
                ) {
                    toleranceValue = progress
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }

        AlertDialog.Builder(this)
            .setCancelable(true)
            .setView(binding.root)
            .setTitle(R.string.dialog_title_sleep_timer)
            .setMessage(R.string.dialog_desc_sleep_timer)
            .setPositiveButton(R.string.dialog_ok) { dialog, _ ->
                lifecycleScope.launch {
                    viewModel.currentSourcePath
                        ?.toDomainTrack(DB.getInstance(this@MainActivity))
                        ?.let {
                            val timerValue = checkNotNull(binding.timerValue)
                            val toleranceValue = checkNotNull(binding.toleranceValue)
                            sharedPreferences.sleepTimerTime = timerValue
                            sharedPreferences.sleepTimerTolerance = toleranceValue
                            viewModel.workManager
                                .beginUniqueWork(
                                    SleepTimerWorker.NAME,
                                    ExistingWorkPolicy.KEEP,
                                    OneTimeWorkRequestBuilder<SleepTimerWorker>().setInputData(
                                        SleepTimerWorker.createInputData(
                                            it.duration,
                                            viewModel.currentPlaybackPositionFlow.value,
                                            System.currentTimeMillis() + timerValue * 60000,
                                            toleranceValue * 60000L
                                        )
                                    )
                                        .build()
                                )
                                .enqueue()
                        }
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_ng) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun onAuthDropboxCompleted() {
        viewModel.storeDropboxApiToken()
    }
}
