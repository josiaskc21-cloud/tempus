package com.eddyizm.tempus.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.media3.session.MediaBrowser;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.service.MediaManager;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.ui.adapter.SongHorizontalAdapter;
import com.google.android.material.color.MaterialColors;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.function.Supplier;

/**
 * Spotify-style "swipe right to add to queue" for song lists.
 * The row only slides a little way out, shows a queue icon, and springs back once released;
 * the song is added through MediaManager.enqueue(..., false), i.e. behind any songs that
 * were already queued and before the rest of the current playlist.
 */
public final class SwipeToQueueHelper {

    private static final float MAX_SLIDE_FRACTION = 0.35f;
    private static final float TRIGGER_FRACTION = 0.25f;

    private SwipeToQueueHelper() {
    }

    public static void attach(@NonNull RecyclerView recyclerView,
                              @NonNull Supplier<SongHorizontalAdapter> adapter,
                              @NonNull Supplier<ListenableFuture<MediaBrowser>> browserFuture) {
        Context context = recyclerView.getContext();
        Drawable icon = ContextCompat.getDrawable(context, R.drawable.ic_queue);
        if (icon != null) {
            icon = DrawableCompat.wrap(icon.mutate());
            DrawableCompat.setTint(icon, MaterialColors.getColor(recyclerView, com.google.android.material.R.attr.colorOnPrimaryContainer));
        }
        final Drawable queueIcon = icon;
        final Paint background = new Paint();
        background.setColor(MaterialColors.getColor(recyclerView, com.google.android.material.R.attr.colorPrimaryContainer));

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            private boolean armed = false;

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                // Never let ItemTouchHelper treat it as a real dismiss; we trigger on release ourselves.
                return Float.MAX_VALUE;
            }

            @Override
            public float getSwipeEscapeVelocity(float defaultValue) {
                return Float.MAX_VALUE;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // Not reached (threshold is unreachable), kept for completeness.
                RecyclerView.Adapter<?> a = viewHolder.getBindingAdapter();
                if (a != null) a.notifyItemChanged(viewHolder.getBindingAdapterPosition());
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {
                View item = viewHolder.itemView;
                float maxSlide = item.getWidth() * MAX_SLIDE_FRACTION;
                float slide = Math.min(dX, maxSlide);

                if (isCurrentlyActive) {
                    boolean nowArmed = dX >= item.getWidth() * TRIGGER_FRACTION;
                    if (nowArmed && !armed) item.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    armed = nowArmed;
                }

                if (slide > 0) {
                    c.drawRect(item.getLeft(), item.getTop(), item.getLeft() + slide, item.getBottom(), background);
                    if (queueIcon != null) {
                        int size = queueIcon.getIntrinsicHeight();
                        int top = item.getTop() + (item.getHeight() - size) / 2;
                        int left = item.getLeft() + Math.round(Math.min(slide, maxSlide) / 2f - size / 2f);
                        queueIcon.setBounds(left, top, left + size, top + size);
                        queueIcon.setAlpha(armed ? 255 : 120);
                        queueIcon.draw(c);
                    }
                }
                super.onChildDraw(c, rv, viewHolder, slide, dY, actionState, isCurrentlyActive);
            }

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(rv, viewHolder);
                if (!armed) return;
                armed = false;

                int position = viewHolder.getBindingAdapterPosition();
                SongHorizontalAdapter songs = adapter.get();
                ListenableFuture<MediaBrowser> future = browserFuture.get();
                if (songs == null || future == null || position == RecyclerView.NO_POSITION || position >= songs.getItemCount()) return;

                Child song = songs.getItem(position);
                MediaManager.enqueue(future, song, false);
                viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                Toast.makeText(rv.getContext(), R.string.swipe_added_to_queue, Toast.LENGTH_SHORT).show();
            }
        }).attachToRecyclerView(recyclerView);
    }
}
