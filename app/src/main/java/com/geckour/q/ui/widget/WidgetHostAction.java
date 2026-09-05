package com.geckour.q.ui.widget;

import android.annotation.SuppressLint;

import androidx.compose.remote.creation.actions.Action;
import androidx.compose.remote.creation.actions.HostAction;
import androidx.compose.remote.creation.compose.action.RemoteAction;
import androidx.compose.remote.creation.compose.state.RemoteStateScope;

/**
 * A click action that emits a plain {@code HOST_ACTION} carrying {@code actionId}.
 *
 * <p>This is the operation the platform's app widget renderer understands: the id it reports back
 * on a click is looked up among the ids registered with
 * {@link android.widget.RemoteViews#setOnClickPendingIntent(int, android.app.PendingIntent)}. It is
 * also what {@code androidx.glance.appwidget}'s own RemoteCompose translator emits.
 *
 * <p>RemoteCompose 1.0.0-alpha18 offers no public way to build one from the Compose creation DSL:
 * {@code pendingIntentAction} and {@code lambdaAction} emit named host actions that only a
 * RemoteCompose <em>player</em> can resolve, {@code RemoteModifier.Element} is sealed, and
 * {@code RemoteAction.toRemoteAction} is Kotlin-{@code internal}. It is however a plain public
 * abstract method on the JVM, so the override lives in Java. Revisit once the library exposes a
 * widget click action.
 */
@SuppressLint("RestrictedApi")
public final class WidgetHostAction extends RemoteAction {

    private final int actionId;

    public WidgetHostAction(int actionId) {
        this.actionId = actionId;
    }

    public int getActionId() {
        return actionId;
    }

    @Override
    public Action toRemoteAction$remote_creation_compose(RemoteStateScope scope) {
        return new HostAction(actionId);
    }
}
