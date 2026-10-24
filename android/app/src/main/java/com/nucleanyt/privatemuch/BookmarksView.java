package com.nucleanyt.privatemuch;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Full-screen bookmarks: shortcuts to sites. Add or remove the current page, open or remove saved shortcuts. */
final class BookmarksView extends FrameLayout {

    interface Listener {
        void onToggleCurrent();
        void onOpen(Bookmarks.Bookmark bookmark);
        void onRemove(Bookmarks.Bookmark bookmark);
        void onDeleteAll();
        void onDismiss();
    }

    private static final int INK = Color.parseColor("#0B1012");
    private static final int SURFACE = Color.parseColor("#131B1E");
    private static final int LINE = Color.parseColor("#24333A");
    private static final int TEXT = Color.parseColor("#E2EBE9");
    private static final int MUTED = Color.parseColor("#8FA29F");
    private static final int FAINT = Color.parseColor("#5B6D6A");
    private static final int TEAL = Color.parseColor("#35D0BF");

    private final Listener listener;
    private final LinearLayout body;
    private final TextView count;
    private boolean confirmingDeleteAll;

    BookmarksView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {Color.parseColor("#11201F"), INK, INK}));
        setClickable(true); // keep touches from reaching the page underneath

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(6), dp(10), dp(16), dp(6));
        ImageButton back = new ImageButton(context);
        back.setImageResource(R.drawable.ic_back);
        back.setContentDescription("Close bookmarks");
        back.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#4035D0BF")), null, oval()));
        back.setOnClickListener(v -> listener.onDismiss());
        AddressBar.addSpringPress(back);
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(4), 0, 0, 0);
        TextView title = new TextView(context);
        title.setText("Bookmarks");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titles.addView(title);
        count = new TextView(context);
        count.setTextColor(MUTED);
        count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        titles.addView(count);
        header.addView(titles);
        column.addView(header);

        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(8), dp(18), dp(28));
        body.setClipChildren(false); // a card's bounce may reach past its edge for a moment; never cut it off
        ScrollView scroll = new ScrollView(context);
        scroll.addView(body);
        column.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        addView(column, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Slides in from the right with a soft settle, like Settings.
        setAlpha(0f);
        setTranslationX(dp(40));
        animate().alpha(1f).translationX(0f).setDuration(340).setInterpolator(new OvershootInterpolator(1.1f));
    }

    /**
     * Rebuilds the screen. currentLabel is the open site's name, or null on the home page
     * (then there's nothing to bookmark). Search results can't be bookmarked (currentAllowed false).
     */
    void show(List<Bookmarks.Bookmark> bookmarks, String currentLabel, boolean currentSaved, boolean currentAllowed, boolean animate) {
        body.removeAllViews();
        int n = bookmarks.size();
        count.setText(n == 0 ? "No bookmarks yet" : n == 1 ? "1 bookmark" : n + " bookmarks");

        if (currentLabel != null) body.addView(currentCard(currentLabel, currentSaved, currentAllowed), cardParams());

        if (n == 0) {
            body.addView(emptyState());
        } else {
            for (int i = 0; i < n; i++) body.addView(row(bookmarks.get(i), animate ? i : -1), cardParams());
        }
        body.addView(footer(n > 0));
    }

    void dismiss(Runnable after) {
        animate().alpha(0f).translationX(dp(40)).setDuration(200).withEndAction(after);
    }

    /** The open page, with a button to add or remove its shortcut. */
    private View currentCard(String label, boolean saved, boolean allowed) {
        Context context = getContext();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(12), dp(12), dp(12));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(SURFACE);
        shape.setCornerRadius(dp(16));
        shape.setStroke(dp(2), Color.parseColor("#5935D0BF"));
        card.setBackground(shape);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView small = new TextView(context);
        small.setText("This page");
        small.setTextColor(MUTED);
        small.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        texts.addView(small);
        TextView name = new TextView(context);
        name.setText(label);
        name.setTextColor(TEXT);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(name);
        card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (!allowed) { // a search's address holds what was typed, so it's never kept
            TextView why = new TextView(context);
            why.setText("Searches can't be bookmarked");
            why.setTextColor(FAINT);
            why.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            why.setGravity(Gravity.END);
            LinearLayout.LayoutParams whyParams = new LinearLayout.LayoutParams(dp(120), ViewGroup.LayoutParams.WRAP_CONTENT);
            whyParams.setMarginStart(dp(10));
            card.addView(why, whyParams);
            return card;
        }

        TextView action = new TextView(context);
        action.setText(saved ? "Bookmarked" : "Bookmark");
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        action.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        action.setGravity(Gravity.CENTER_VERTICAL);
        action.setCompoundDrawablePadding(dp(6));
        action.setPadding(dp(12), dp(8), dp(14), dp(8));
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(18));
        if (saved) { // already a bookmark: a quiet outline; tapping removes it
            action.setTextColor(TEAL);
            action.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_bookmark_small, 0, 0, 0);
            pill.setStroke(dp(1), Color.parseColor("#8035D0BF"));
            action.setContentDescription("Remove this page from bookmarks");
        } else {
            action.setTextColor(INK);
            pill.setColor(TEAL);
            action.setContentDescription("Bookmark this page");
        }
        action.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#4035D0BF")), pill, null));
        action.setOnClickListener(v -> listener.onToggleCurrent());
        AddressBar.addSpringPress(action);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.setMarginStart(dp(10));
        card.addView(action, actionParams);
        return card;
    }

    private View row(Bookmarks.Bookmark bookmark, int index) {
        Context context = getContext();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(10), dp(4), dp(10));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(SURFACE);
        shape.setCornerRadius(dp(14));
        shape.setStroke(dp(1), LINE);
        card.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#3335D0BF")), shape, null));

        ImageView mark = new ImageView(context);
        mark.setImageResource(R.drawable.ic_bookmark_small);
        card.addView(mark, new LinearLayout.LayoutParams(dp(16), dp(16)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, dp(6), 0);
        String host = AddressBar.displayName(bookmark.url);
        TextView name = new TextView(context);
        name.setText(host); // only the address is kept, so the site's name and page come from it
        name.setTextColor(TEXT);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(name);
        TextView site = new TextView(context);
        site.setText(pageOf(bookmark.url));
        site.setTextColor(MUTED);
        site.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        site.setSingleLine(true);
        site.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(site);
        card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton remove = new ImageButton(context);
        remove.setImageResource(R.drawable.ic_clear);
        remove.setContentDescription("Remove bookmark");
        remove.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#4035D0BF")), null, oval()));
        remove.setOnClickListener(v -> card.animate().alpha(0f).translationX(dp(30)).setDuration(180)
                .withEndAction(() -> listener.onRemove(bookmark)));
        AddressBar.addSpringPress(remove);
        card.addView(remove, new LinearLayout.LayoutParams(dp(40), dp(40)));

        card.setOnClickListener(v -> listener.onOpen(bookmark));
        AddressBar.addSpringPress(card, 0.97f);

        if (index >= 0) { // rows rise in one after another and settle with a small bounce
            card.setAlpha(0f);
            card.setTranslationY(dp(14));
            card.animate().alpha(1f).translationY(0f).setStartDelay(35L * Math.min(index, 10))
                    .setDuration(400).setInterpolator(new OvershootInterpolator(1.5f));
        }
        return card;
    }

    private View emptyState() {
        Context context = getContext();
        LinearLayout empty = new LinearLayout(context);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        empty.setPadding(dp(24), dp(30), dp(24), dp(10));
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_bookmark);
        icon.setAlpha(0.6f);
        empty.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView text = new TextView(context);
        text.setText("No bookmarks yet. Open a site, then tap the bookmark button to add a shortcut to it here.");
        text.setTextColor(MUTED);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        text.setGravity(Gravity.CENTER);
        text.setLineSpacing(0, 1.2f);
        text.setPadding(0, dp(10), 0, 0);
        empty.addView(text);
        return empty;
    }

    /** What a bookmark keeps (only the address), and Remove all (which asks for a second tap). */
    private View footer(boolean hasBookmarks) {
        Context context = getContext();
        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        footer.setPadding(dp(12), dp(18), dp(12), 0);

        TextView note = new TextView(context);
        note.setText("A bookmark is only a shortcut. Just the site's address is kept, on this phone inside PrivateMuch: no page data, cookies or history, and it's never backed up or sent anywhere. Opening one is a fresh, private visit.");
        note.setTextColor(FAINT);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        note.setGravity(Gravity.CENTER);
        note.setLineSpacing(0, 1.2f);
        note.setPadding(0, 0, 0, dp(14));
        footer.addView(note);

        if (hasBookmarks) {
            confirmingDeleteAll = false;
            TextView deleteAll = new TextView(context);
            deleteAll.setText("Remove all bookmarks");
            deleteAll.setTextColor(TEAL);
            deleteAll.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            deleteAll.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            deleteAll.setPadding(dp(18), dp(10), dp(18), dp(10));
            GradientDrawable outline = new GradientDrawable();
            outline.setCornerRadius(dp(18));
            outline.setStroke(dp(1), Color.parseColor("#8035D0BF"));
            deleteAll.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#4035D0BF")), outline, null));
            deleteAll.setOnClickListener(v -> {
                if (confirmingDeleteAll) {
                    listener.onDeleteAll();
                    return;
                }
                confirmingDeleteAll = true; // removed bookmarks can't be brought back, so ask once more
                deleteAll.setText("Tap again to remove all");
                deleteAll.postDelayed(() -> {
                    confirmingDeleteAll = false;
                    deleteAll.setText("Remove all bookmarks");
                }, 3000);
            });
            AddressBar.addSpringPress(deleteAll);
            footer.addView(deleteAll);
        }
        return footer;
    }

    /** The page part of the address ("/wiki/Duck"), or "Home page" for the site's front page. */
    static String pageOf(String url) {
        String path = Uri.parse(url).getPath();
        return path == null || path.isEmpty() || path.equals("/") ? "Home page" : path;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        return params;
    }

    private static GradientDrawable oval() {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(Color.WHITE);
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
