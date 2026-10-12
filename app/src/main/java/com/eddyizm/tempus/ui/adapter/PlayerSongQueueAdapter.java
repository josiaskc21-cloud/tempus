package com.eddyizm.tempus.ui.adapter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.ColorUtils;
import androidx.media3.session.MediaBrowser;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.RequestBuilder;
import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.ItemPlayerQueueSongBinding;
import com.eddyizm.tempus.glide.CustomGlideRequest;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.interfaces.MediaIndexCallback;
import com.eddyizm.tempus.service.DownloaderManager;
import com.eddyizm.tempus.service.MediaManager;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.util.DownloadUtil;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.ExternalAudioReader;
import com.eddyizm.tempus.util.MusicUtil;
import com.eddyizm.tempus.util.Preferences;
import com.eddyizm.tempus.util.FavoriteRegistry;
import com.google.android.material.color.MaterialColors;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSongQueueAdapter extends RecyclerView.Adapter<PlayerSongQueueAdapter.ViewHolder> {
    private static final String TAG = "PlayerSongQueueAdapter";
    private final ClickCallback click;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;
    private List<Child> songs;
    private final Map<String, Boolean> downloadStatusCache = new ConcurrentHashMap<>();
    private String currentPlayingId;
    private boolean isPlaying;
    private List<Integer> currentPlayingPositions = Collections.emptyList();

    public PlayerSongQueueAdapter(ClickCallback click) {
        this.click = click;
        this.songs = Collections.emptyList();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPlayerQueueSongBinding view = ItemPlayerQueueSongBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Child song = songs.get(holder.getLayoutPosition());

        holder.item.queueSongTitleTextView.setText(song.getTitle());
        holder.item.queueSongSubtitleTextView.setText(
                holder.itemView.getContext().getString(
                        R.string.song_subtitle_formatter,
                        song.getArtist(),
                        MusicUtil.getReadableDurationString(song.getDuration(), false),
                        MusicUtil.getReadableAudioQualityString(song)
                )
        );

        RequestBuilder<Drawable> thumbnail = CustomGlideRequest.Builder
                        .from(holder.itemView.getContext(), song.getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                        .build()
                        .sizeMultiplier(0.1f);

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), song.getCoverArtId(), CustomGlideRequest.ResourceType.Song)
                .build()
                .thumbnail(thumbnail)
                .into(holder.item.queueSongCoverImageView);
        // Played songs are dimmed; songs the user queued ("Next in queue") get a tinted row.
        int pos = holder.getLayoutPosition();
        float alpha = (currentIndex >= 0 && pos < currentIndex) ? 0.2f : 1.0f;
        holder.item.queueSongTitleTextView.setAlpha(alpha);
        holder.item.queueSongSubtitleTextView.setAlpha(alpha);
        holder.item.ratingIndicatorImageView.setAlpha(alpha);
        if (isInUpNext(pos)) {
            int tint = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorSecondaryContainer);
            holder.itemView.setBackgroundColor(ColorUtils.setAlphaComponent(tint, 150));
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }

        boolean isDownloaded = false;

        if (Preferences.getDownloadDirectoryUri() == null) {
            DownloaderManager downloaderManager = DownloadUtil.getDownloadTracker(holder.itemView.getContext());
            if (downloaderManager != null) {
                isDownloaded = downloaderManager.isDownloaded(song.getId());
            }
        } else {
            isDownloaded = ExternalAudioReader.getUri(song) != null;
        }

        if (isDownloaded) {
            holder.item.downloadIndicatorIcon.setVisibility(View.VISIBLE);
        } else {
            holder.item.downloadIndicatorIcon.setVisibility(View.GONE);
        }

        if (Preferences.showItemRating()) {
            boolean isStarred = FavoriteRegistry.resolve(FavoriteRegistry.Kind.SONG, song.getId(), song.getStarred() != null);
            holder.item.ratingIndicatorImageView.setVisibility(isStarred || song.getUserRating() != null ? View.VISIBLE : View.GONE);

            holder.item.preferredIcon.setVisibility(isStarred ? View.VISIBLE : View.GONE);
            holder.item.ratingBarLayout.setVisibility(song.getUserRating() != null ? View.VISIBLE : View.GONE);

            if (song.getUserRating() != null) {
                holder.item.oneStarIcon.setImageDrawable(AppCompatResources.getDrawable(holder.itemView.getContext(), song.getUserRating() >= 1 ? R.drawable.ic_star : R.drawable.ic_star_outlined));
                holder.item.twoStarIcon.setImageDrawable(AppCompatResources.getDrawable(holder.itemView.getContext(), song.getUserRating() >= 2 ? R.drawable.ic_star : R.drawable.ic_star_outlined));
                holder.item.threeStarIcon.setImageDrawable(AppCompatResources.getDrawable(holder.itemView.getContext(), song.getUserRating() >= 3 ? R.drawable.ic_star : R.drawable.ic_star_outlined));
                holder.item.fourStarIcon.setImageDrawable(AppCompatResources.getDrawable(holder.itemView.getContext(), song.getUserRating() >= 4 ? R.drawable.ic_star : R.drawable.ic_star_outlined));
                holder.item.fiveStarIcon.setImageDrawable(AppCompatResources.getDrawable(holder.itemView.getContext(), song.getUserRating() >= 5 ? R.drawable.ic_star : R.drawable.ic_star_outlined));
            }
        } else {
            holder.item.ratingIndicatorImageView.setVisibility(View.GONE);
        }
        holder.itemView.setOnClickListener(v -> {
            if (com.eddyizm.tempus.lan.LanRemoteSession.isActive()) {
                int pos = holder.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                com.eddyizm.tempus.lan.LanRemoteState state = com.eddyizm.tempus.lan.LanRemoteSession.current();
                if (pos == state.getIndex()) com.eddyizm.tempus.lan.LanRemoteSession.command(state.getPlayWhenReady() ? "pause" : "play");
                else com.eddyizm.tempus.lan.LanRemoteSession.routeEdit("select", pos, pos);
                return;
            }
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                    int pos = holder.getBindingAdapterPosition();
                    Child s = songs.get(pos);
                    if (currentPlayingId != null && currentPlayingId.equals(s.getId())) {
                        if (isPlaying) {
                            mediaBrowser.pause();
                        } else {
                            mediaBrowser.play();
                        }
                    } else {
                        mediaBrowser.seekTo(pos, 0);
                        mediaBrowser.play();
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error obtaining MediaBrowser", e);
                }
            }, MoreExecutors.directExecutor());

        });
        bindPlaybackState(holder, song);
    }

    private void bindPlaybackState(@NonNull PlayerSongQueueAdapter.ViewHolder holder, @NonNull Child song) {
        // Prefer the player's index: the same song can be in the queue twice (queued from the
        // playlist that is playing), and only one of those rows is actually playing.
        boolean isCurrent = currentIndex >= 0
                ? holder.getLayoutPosition() == currentIndex
                : currentPlayingId != null && currentPlayingId.equals(song.getId());

        if (isCurrent) {
            holder.item.playPauseIcon.setVisibility(View.VISIBLE);
            if (isPlaying) {
                holder.item.playPauseIcon.setImageResource(R.drawable.ic_pause);
            } else {
                holder.item.playPauseIcon.setImageResource(R.drawable.ic_play);
            }
            holder.item.coverArtOverlay.setVisibility(View.VISIBLE);
        } else {
            holder.item.playPauseIcon.setVisibility(View.INVISIBLE);
            holder.item.coverArtOverlay.setVisibility(View.INVISIBLE);
        }
    }
    
    private int currentIndex = -1;
    private int upNextEnd = -1;

    /** Player position of the current song and end of the user-queued block (or -1). */
    public void setQueuePositions(int currentIndex, int upNextEnd) {
        if (this.currentIndex == currentIndex && this.upNextEnd == upNextEnd) return;
        this.currentIndex = currentIndex;
        this.upNextEnd = upNextEnd;
        notifyDataSetChanged();
    }

    private boolean hasUpNextBlock() {
        return currentIndex >= 0 && upNextEnd > currentIndex + 1 && upNextEnd <= getItemCount();
    }

    private boolean isInUpNext(int position) {
        return hasUpNextBlock() && position > currentIndex && position < upNextEnd;
    }

    /** Section title shown above this row, or null. */
    @Nullable
    public String sectionLabelAt(Context context, int position) {
        if (currentIndex < 0 || position == RecyclerView.NO_POSITION || position >= getItemCount()) return null;
        boolean block = hasUpNextBlock();
        if (block && position == currentIndex + 1) return context.getString(R.string.queue_section_next_in_queue);
        if (position == (block ? upNextEnd : currentIndex + 1)) return context.getString(R.string.queue_section_next_up);
        return null;
    }

    /** Draws the "Next in queue" / "Next up" headers without changing adapter positions. */
    public static class SectionDecoration extends RecyclerView.ItemDecoration {
        private final PlayerSongQueueAdapter adapter;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int headerHeight;
        private final int startPadding;

        public SectionDecoration(@NonNull Context context, @NonNull PlayerSongQueueAdapter adapter) {
            this.adapter = adapter;
            float density = context.getResources().getDisplayMetrics().density;
            headerHeight = Math.round(36 * density);
            startPadding = Math.round(16 * density);
            paint.setTextSize(14 * context.getResources().getDisplayMetrics().scaledDensity);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int pos = parent.getChildAdapterPosition(view);
            outRect.top = adapter.sectionLabelAt(parent.getContext(), pos) != null ? headerHeight : 0;
        }

        @Override
        public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            paint.setColor(MaterialColors.getColor(parent, com.google.android.material.R.attr.colorPrimary));
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                String label = adapter.sectionLabelAt(parent.getContext(), parent.getChildAdapterPosition(child));
                if (label == null) continue;
                float baseline = child.getTop() + child.getTranslationY() - headerHeight / 2f - (paint.descent() + paint.ascent()) / 2f;
                c.drawText(label, parent.getPaddingLeft() + startPadding, baseline, paint);
            }
        }
    }

    public List<Child> getItems() {
        return this.songs;
    }

    public void setItems(List<Child> songs) {
        this.songs = songs;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        if (songs == null) {
            return 0;
        }
        return songs.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public void setMediaBrowserListenableFuture(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture) {
        this.mediaBrowserListenableFuture = mediaBrowserListenableFuture;
    }

    public void setPlaybackState(String mediaId, boolean playing) {
        String oldId = this.currentPlayingId;
        boolean oldPlaying = this.isPlaying;
        List<Integer> oldPositions = currentPlayingPositions;

        this.currentPlayingId = mediaId;
        this.isPlaying = playing;

        if (Objects.equals(oldId, mediaId) && oldPlaying == playing) {
            List<Integer> newPositionsCheck = mediaId != null ? findPositionsById(mediaId) : Collections.emptyList();
            if (oldPositions.equals(newPositionsCheck)) {
                return;
            }
        }

        currentPlayingPositions = mediaId != null ? findPositionsById(mediaId) : Collections.emptyList();

        for (int pos : oldPositions) {
            if (pos >= 0 && pos < songs.size()) {
                notifyItemChanged(pos, "payload_playback");
            }
        }
        for (int pos : currentPlayingPositions) {
            if (!oldPositions.contains(pos) && pos >= 0 && pos < songs.size()) {
                notifyItemChanged(pos, "payload_playback");
            }
        }
    }

    private List<Integer> findPositionsById(String id) {
        if (id == null) return Collections.emptyList();
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < songs.size(); i++) {
            if (id.equals(songs.get(i).getId())) {
                positions.add(i);
            }
        }
        return positions;
    }

    public Child getItem(int id) {
        return songs.get(id);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemPlayerQueueSongBinding item;

        ViewHolder(ItemPlayerQueueSongBinding item) {
            super(item.getRoot());

            this.item = item;

            item.queueSongTitleTextView.setSelected(true);
            item.queueSongSubtitleTextView.setSelected(true);

            itemView.setOnClickListener(v -> onClick());
        }

        public void onClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList(Constants.TRACKS_OBJECT, new ArrayList<>(songs));
            bundle.putInt(Constants.ITEM_POSITION, getBindingAdapterPosition());

            click.onMediaClick(bundle);
        }
    }
}
