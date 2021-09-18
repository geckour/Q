package com.geckour.q.ui.main

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.dropbox.core.android.Auth
import com.geckour.q.BuildConfig
import com.geckour.q.R
import com.geckour.q.data.db.DB
import com.geckour.q.databinding.ActivityMainBinding
import com.geckour.q.databinding.DialogSleepBinding
import com.geckour.q.domain.model.DomainTrack
import com.geckour.q.domain.model.RequestedTransaction
import com.geckour.q.service.DropboxMediaRetrieveService
import com.geckour.q.service.LocalMediaRetrieveService
import com.geckour.q.service.SleepTimerService
import com.geckour.q.ui.easteregg.EasterEggFragment
import com.geckour.q.ui.equalizer.EqualizerFragment
import com.geckour.q.ui.library.album.AlbumListFragment
import com.geckour.q.ui.library.artist.ArtistListFragment
import com.geckour.q.ui.library.genre.GenreListFragment
import com.geckour.q.ui.library.track.TrackListFragment
import com.geckour.q.ui.pay.PaymentFragment
import com.geckour.q.ui.pay.PaymentViewModel
import com.geckour.q.ui.setting.SettingActivity
import com.geckour.q.ui.sheet.BottomSheetFragment
import com.geckour.q.util.dropboxToken
import com.geckour.q.util.ducking
import com.geckour.q.util.isNightMode
import com.geckour.q.util.preferScreen
import com.geckour.q.util.sleepTimerTime
import com.geckour.q.util.sleepTimerTolerance
import com.geckour.q.util.toDomainTrack
import com.geckour.q.util.toNightModeInt
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import permissions.dispatcher.ktx.constructPermissionsRequest
import timber.log.Timber
import java.io.File
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    enum class RequestCode(val code: Int) {
        RESULT_SETTING(333)
    }

    companion object {

        private const val ACTION_SYNCING = "action_syncing"
        private const val EXTRA_SYNCING_PROGRESS_NUMERATOR = "extra_syncing_progress_numerator"
        private const val EXTRA_SYNCING_PROGRESS_DENOMINATOR = "extra_syncing_progress_denominator"
        private const val EXTRA_SYNCING_PROGRESS_PATH = "extra_syncing_progress_path"
        private const val EXTRA_SYNCING_COMPLETE = "extra_syncing_complete"
        private const val STATE_KEY_REQUESTED_TRANSACTION = "state_key_requested_transaction"

        fun createIntent(context: Context): Intent = Intent(context, MainActivity::class.java)

        fun createProgressIntent(
            progressNumerator: Int,
            progressDenominator: Int? = null,
            progressPath: String? = null
        ) = Intent(ACTION_SYNCING).apply {
            putExtra(EXTRA_SYNCING_PROGRESS_NUMERATOR, progressNumerator)
            putExtra(EXTRA_SYNCING_PROGRESS_DENOMINATOR, progressDenominator)
            putExtra(EXTRA_SYNCING_PROGRESS_PATH, progressPath)
        }

        fun createSyncCompleteIntent(complete: Boolean) = Intent(ACTION_SYNCING).apply {
            putExtra(EXTRA_SYNCING_COMPLETE, complete)
        }
    }

    private val viewModel: MainViewModel by viewModels()

    private val paymentViewModel: PaymentViewModel by viewModels()

    internal lateinit var binding: ActivityMainBinding
    private val sharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    private lateinit var gestureDetector: GestureDetector

    private lateinit var drawerToggle: ActionBarDrawerToggle

    private val searchListAdapter: SearchListAdapter by lazy { SearchListAdapter(viewModel) }

    private var requestedTransaction: RequestedTransaction? = null
    private var paused = true

    private val syncingProgressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.extras?.let { extras ->
                extras.getBoolean(EXTRA_SYNCING_COMPLETE, false)?.let {
                    if (it) {
                        viewModel.syncing = false
                        setLockingIndicator()
                        viewModel.forceLoad.postValue(Unit)
                    }
                }
                extras.getInt(EXTRA_SYNCING_PROGRESS_NUMERATOR, -1).let progress@{ numerator ->
                    if (numerator < 0) return@progress

                    val denominator = extras.getInt(EXTRA_SYNCING_PROGRESS_DENOMINATOR, -1)
                    val path = extras.getString(EXTRA_SYNCING_PROGRESS_PATH)
                    viewModel.syncing = true
                    setLockingIndicator()
                    binding.indicatorLocking.progressSync.text =
                        if (denominator < 0) getString(R.string.progress_sync_dropbox, numerator)
                        else getString(R.string.progress_sync, numerator, denominator)
                    binding.indicatorLocking.progressPath.text = path
                }
            }
        }
    }

    private val onNavigationItemSelected: (MenuItem) -> Boolean = {
        when (it.itemId) {
            R.id.nav_artist -> ArtistListFragment.newInstance()
            R.id.nav_album -> AlbumListFragment.newInstance()
            R.id.nav_track -> TrackListFragment.newInstance()
            R.id.nav_genre -> GenreListFragment.newInstance()
            R.id.nav_setting -> {
                startActivityForResult(
                    SettingActivity.createIntent(this), RequestCode.RESULT_SETTING.code
                )
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
                val token = sharedPreferences.dropboxToken
                if (token.isBlank()) {
                    viewModel.isDropboxAuthOngoing = true
                    Auth.startOAuth2Authentication(this, BuildConfig.DROPBOX_APP_KEY)
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

    private fun retrieveMedia(onlyAdded: Boolean) {
        constructPermissionsRequest(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            onPermissionDenied = ::onReadExternalStorageDenied
        ) {
            startService(LocalMediaRetrieveService.getIntent(this, false, onlyAdded))
        }.launch()
    }

    private fun retrieveDropboxMedia(rootPath: String) {
        constructPermissionsRequest(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            onPermissionDenied = ::onReadExternalStorageDenied
        ) {
            startService(DropboxMediaRetrieveService.getIntent(this, rootPath, false))
        }.launch()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        binding.contentSearch.recyclerView.adapter = searchListAdapter

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent?,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                return if (abs(velocityX) > abs(velocityY)) {
                    binding.drawerLayout.apply {
                        if (velocityX > 0) openDrawer(GravityCompat.START)
                        else closeDrawer(GravityCompat.START)
                    }
                    true
                } else false
            }
        })

        observeEvents()
        registerReceiver(syncingProgressReceiver, IntentFilter(ACTION_SYNCING))

        setSupportActionBar(binding.toolbar)
        setupDrawer()

        if (savedInstanceState == null) {
            viewModel.checkDBIsEmpty { retrieveMedia(false) }
            retrieveMedia(true)

            val navId = PreferenceManager.getDefaultSharedPreferences(this).preferScreen.value.navId
            onNavigationItemSelected(binding.navigationView.menu.findItem(navId))
        } else if (savedInstanceState.containsKey(STATE_KEY_REQUESTED_TRANSACTION)) {
            requestedTransaction =
                savedInstanceState.getParcelable(STATE_KEY_REQUESTED_TRANSACTION) as RequestedTransaction?
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

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        return if (gestureDetector.onTouchEvent(ev)) true else super.dispatchTouchEvent(ev)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        requestedTransaction?.apply {
            outState.putParcelable(STATE_KEY_REQUESTED_TRANSACTION, this)
        }
    }

    override fun onPause() {
        super.onPause()

        paused = true
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(syncingProgressReceiver)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            RequestCode.RESULT_SETTING.code -> {
                if (viewModel.player.value?.getDuking() != sharedPreferences.ducking) {
                    viewModel.rebootPlayer()
                }
            }
        }
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
            viewModel.loading.collectLatest { setLockingIndicator() }
        }

        viewModel.selectedArtist.observe(this) {
            it ?: return@observe
            requestedTransaction = RequestedTransaction(
                RequestedTransaction.Tag.ARTIST, artist = it
            )
            tryTransaction()
        }

        viewModel.selectedAlbum.observe(this) {
            it ?: return@observe
            requestedTransaction = RequestedTransaction(RequestedTransaction.Tag.ALBUM, album = it)
            tryTransaction()
        }

        viewModel.selectedGenre.observe(this) {
            it ?: return@observe
            requestedTransaction = RequestedTransaction(RequestedTransaction.Tag.GENRE, genre = it)
            tryTransaction()
        }

        viewModel.trackToDelete.observe(this) {
            it ?: return@observe
            deleteFromDevice(it)
        }

        viewModel.searchItems.observe(this) {
            if (it == null) {
                binding.contentSearch.root.visibility = View.GONE
                return@observe
            } else binding.contentSearch.root.visibility = View.VISIBLE

            searchListAdapter.replaceItems(it)
        }

        lifecycleScope.launch {
            viewModel.dropboxItemList.collectLatest {
                it ?: return@collectLatest
                (dropboxChooserDialog ?: run {
                    DropboxChooserDialog(
                        this@MainActivity,
                        onClickItem = { metadata ->
                            viewModel.showDropboxFolderChooser(metadata)
                        },
                        onChoose = { path ->
                            retrieveDropboxMedia(path)
                        }
                    ).apply { dropboxChooserDialog = this }
                }).show(it.first, it.second)
            }
        }

        paymentViewModel.saveSuccess.observe(this) {
            if (it == null) return@observe
            Snackbar.make(
                binding.root, if (it) R.string.payment_save_success
                else R.string.payment_save_failure, Snackbar.LENGTH_SHORT
            ).show()
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
                requestedTransaction = RequestedTransaction(RequestedTransaction.Tag.EASTER_EGG)
                tryTransaction()
                true
            }
    }

    private fun setLockingIndicator() {
        when {
            viewModel.syncing -> {
                indicateSync()
                toggleIndicateLock(true)
            }
            viewModel.loading.value.first -> {
                indicateLoad(viewModel.loading.value.second)
                toggleIndicateLock(true)
            }
            else -> {
                toggleIndicateLock(false)
            }
        }
    }

    private fun indicateSync() {
        binding.indicatorLocking.descLocking.text = getString(R.string.syncing)
        binding.indicatorLocking.progressSync.visibility = View.VISIBLE
        binding.indicatorLocking.buttonCancel.apply {
            setOnClickListener { viewModel.onCancelSync(this@MainActivity) }
            visibility = View.VISIBLE
        }
    }

    private fun indicateLoad(onAbort: (() -> Unit)?) {
        binding.indicatorLocking.descLocking.text = getString(R.string.loading)
        binding.indicatorLocking.progressSync.visibility = View.GONE
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
            requestedTransaction?.apply {
                Timber.d("qgeck requested transaction: $requestedTransaction")
                dismissSearch()
                when (this.tag) {
                    RequestedTransaction.Tag.ARTIST -> {
                        if (artist != null) {
                            supportFragmentManager.beginTransaction().replace(
                                R.id.content_main, AlbumListFragment.newInstance(
                                    artist
                                ), artist.title
                            ).addToBackStack(
                                null
                            ).commit()
                        }
                    }
                    RequestedTransaction.Tag.ALBUM -> {
                        if (album != null) {
                            supportFragmentManager.beginTransaction().replace(
                                R.id.content_main, TrackListFragment.newInstance(
                                    album
                                ), album.title
                            ).addToBackStack(
                                null
                            ).commit()
                        }
                    }
                    RequestedTransaction.Tag.GENRE -> {
                        if (genre != null) {
                            supportFragmentManager.beginTransaction().replace(
                                R.id.content_main, TrackListFragment.newInstance(
                                    genre
                                ), genre.name
                            ).addToBackStack(
                                null
                            ).commit()
                        }
                    }
                    RequestedTransaction.Tag.EASTER_EGG -> {
                        supportFragmentManager.beginTransaction().replace(
                            R.id.content_main, EasterEggFragment.newInstance()
                        ).addToBackStack(
                            null
                        ).commit()
                        binding.drawerLayout.closeDrawer(binding.navigationView)
                    }
                }
                requestedTransaction = null
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
                    viewModel.player.value?.removeQueue(domainTrack)
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
                    viewModel.player.value
                        ?.currentMediaSource
                        ?.toDomainTrack(DB.getInstance(this@MainActivity))
                        ?.let {
                            val timerValue = binding.timerValue!!
                            val toleranceValue = binding.toleranceValue!!
                            sharedPreferences.sleepTimerTime = timerValue
                            sharedPreferences.sleepTimerTolerance = toleranceValue
                            SleepTimerService.start(
                                this@MainActivity,
                                it,
                                viewModel.player.value
                                    ?.playbackPositionFLow
                                    ?.value ?: return@launch,
                                System.currentTimeMillis() + timerValue * 60000,
                                toleranceValue * 60000L
                            )
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
