package com.nucleanyt.privatemuch;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
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

/** Full-screen private tab overview: a two-column grid of tab cards with previews, New tab and Close all. */
final class TabsView extends FrameLayout {

    interface Listener {
        void onSelect(Tab tab);
        void onClose(Tab tab);
        void onNewTab();
        void onCloseAll();
        void onDismiss();
    }

    private static final int INK = Color.parseColor("#0B1012");
    private static final int SURFACE = Color.parseColor("#131B1E");
    private static final int SURFACE_2 = Color.parseColor("#192427");
    private static final int LINE = Color.parseColor("#24333A");
    private static final int TEXT = Color.parseColor("#E2EBE9");
    private static final int MUTED = Color.parseColor("#8FA29F");
    private static final int TEAL = Color.parseColor("#35D0BF");

    private final List<Tab> tabs;
    private final Listener listener;
    private final LinearLayout grid;
    private final TextView count;
    private Tab current;

    TabsView(Context context, List<Tab> tabs, Tab current, Listener listener) {
        super(context);
        this.tabs = tabs;
        this.current = current;
        this.listener = listener;
        // A faint teal glow at the top fading into the app's ink, like the home page.
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {Color.parseColor("#11201F"), INK, INK}));
        setClickable(true); // keep touches from reaching the page underneath

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(14), dp(10), dp(10));

        // The same eye-with-a-line badge as the home page, so it's clear these tabs are private.
        ImageView badge = new ImageView(context);
        badge.setImageResource(R.drawable.ic_incognito);
        badge.setScaleType(ImageView.ScaleType.CENTER);
        GradientDrawable badgeShape = new GradientDrawable();
        badgeShape.setShape(GradientDrawable.OVAL);
        badgeShape.setColor(SURFACE);
        badgeShape.setStroke(dp(1), Color.parseColor("#5935D0BF"));
        badge.setBackground(badgeShape);
        header.addView(badge, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(12), 0, dp(8), 0);
        TextView title = new TextView(context);
        title.setText("Private tabs");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titles.addView(title);
        count = new TextView(context);
        count.setTextColor(MUTED);
        count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        titles.addView(count);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView newTab = new TextView(context);
        newTab.setText("New tab");
        newTab.setTextColor(INK);
        newTab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        newTab.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        newTab.setGravity(Gravity.CENTER_VERTICAL);
        newTab.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_plus, 0, 0, 0);
        newTab.setCompoundDrawablePadding(dp(6));
        newTab.setPadding(dp(12), dp(9), dp(16), dp(9));
        GradientDrawable teal = new GradientDrawable();
        teal.setColor(TEAL);
        teal.setCornerRadius(dp(18));
        newTab.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#55FFFFFF")), teal, null));
        newTab.setOnClickListener(v -> listener.onNewTab());
        AddressBar.addSpringPress(newTab);
        header.addView(newTab);

        ImageButton close = new ImageButton(context);
        close.setImageResource(R.drawable.ic_clear);
        close.setContentDescription("Close tab overview");
        close.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#4035D0BF")), null, oval()));
        close.setOnClickListener(v -> listener.onDismiss());
        AddressBar.addSpringPress(close);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        closeParams.setMarginStart(dp(6));
        header.addView(close, closeParams);

        column.addView(header);

        ScrollView scroll = new ScrollView(context);
        scroll.setClipToPadding(false);
        grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(14), dp(4), dp(14), dp(24));
        scroll.addView(grid);
        column.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        addView(column, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        build();

        // Opens with a soft zoom and fade.
        setAlpha(0f);
        setScaleX(1.04f);
        setScaleY(1.04f);
        animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new OvershootInterpolator(1.2f));
    }

    void refresh(Tab current) {
        this.current = current;
        build();
    }

    void dismiss(Runnable after) {
        animate().alpha(0f).scaleX(1.04f).scaleY(1.04f).setDuration(200).withEndAction(after);
    }

    private void build() {
        grid.removeAllViews();
        int n = tabs.size();
        count.setText((n == 1 ? "1 tab" : n + " tabs") + " · history-free");
        for (int i = 0; i < n; i += 2) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < i + 2; j++) {
                LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                cell.setMargins(dp(6), dp(6), dp(6), dp(6));
                if (j < n) {
                    row.addView(card(tabs.get(j), j), cell);
                } else {
                    row.addView(new View(getContext()), cell); // keeps a lone last card at half width
                }
            }
            grid.addView(row);
        }
        grid.addView(footer());
    }

    /** What makes these tabs private, and a way to close them all at once. */
    private View footer() {
        Context context = getContext();
        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        footer.setPadding(dp(24), dp(22), dp(24), 0);

        ImageView eye = new ImageView(context);
        eye.setImageResource(R.drawable.ic_incognito);
        eye.setAlpha(0.7f);
        footer.addView(eye, new LinearLayout.LayoutParams(dp(28), dp(28)));

        TextView note = new TextView(context);
        note.setText("These tabs keep no history. Pages, cookies and site data are wiped when you close PrivateMuch.");
        note.setTextColor(MUTED);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        note.setGravity(Gravity.CENTER);
        note.setLineSpacing(0, 1.2f);
        note.setPadding(0, dp(8), 0, dp(14));
        footer.addView(note);

        TextView closeAll = new TextView(context);
        closeAll.setText("Close all tabs");
        closeAll.setTextColor(TEAL);
        closeAll.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        closeAll.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        closeAll.setPadding(dp(18), dp(10), dp(18), dp(10));
        GradientDrawable outline = new GradientDrawable();
        outline.setCornerRadius(dp(18));
        outline.setStroke(dp(1), Color.parseColor("#8035D0BF"));
        closeAll.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#4035D0BF")), outline, null));
        closeAll.setOnClickListener(v -> listener.onCloseAll());
        AddressBar.addSpringPress(closeAll);
        footer.addView(closeAll);

        footer.setAlpha(0f);
        footer.animate().alpha(1f).setStartDelay(200).setDuration(400);
        return footer;
    }

    private View card(Tab tab, int index) {
        Context context = getContext();
        FrameLayout card = new FrameLayout(context);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(SURFACE);
        shape.setCornerRadius(dp(16));
        boolean isCurrent = tab == current;
        shape.setStroke(dp(isCurrent ? 2 : 1), isCurrent ? TEAL : LINE);
        card.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#3335D0BF")), shape, null));
        card.setClipToOutline(true); // rounded corners also clip the preview
        card.setPadding(dp(isCurrent ? 2 : 1), dp(isCurrent ? 2 : 1), dp(isCurrent ? 2 : 1), dp(10));

        LinearLayout inside = new LinearLayout(context);
        inside.setOrientation(LinearLayout.VERTICAL);

        ImageView preview = new ImageView(context);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackgroundColor(SURFACE_2);
        if (tab.preview != null) {
            preview.setImageBitmap(tab.preview);
        } else {
            preview.setScaleType(ImageView.ScaleType.CENTER);
            preview.setImageResource(R.drawable.ic_home);
        }
        inside.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170)));

        TextView name = new TextView(context);
        name.setText(tab.label());
        name.setTextColor(TEXT);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(dp(10), dp(9), dp(10), 0);
        inside.addView(name);

        TextView host = new TextView(context);
        host.setText(tab.onHome() ? "PrivateMuch" : AddressBar.displayName(tab.url()));
        host.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_incognito_small, 0, 0, 0);
        host.setCompoundDrawablePadding(dp(5));
        host.setGravity(Gravity.CENTER_VERTICAL);
        host.setTextColor(MUTED);
        host.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        host.setSingleLine(true);
        host.setEllipsize(TextUtils.TruncateAt.END);
        host.setPadding(dp(10), dp(1), dp(10), 0);
        inside.addView(host);

        card.addView(inside);

        ImageButton close = new ImageButton(context);
        close.setImageResource(R.drawable.ic_clear);
        close.setContentDescription("Close tab");
        GradientDrawable dot = oval();
        dot.setColor(Color.parseColor("#CC0B1012"));
        close.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#4035D0BF")), dot, null));
        close.setPadding(dp(5), dp(5), dp(5), dp(5));
        close.setScaleType(ImageView.ScaleType.FIT_CENTER);
        close.setOnClickListener(v -> card.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(180)
                .withEndAction(() -> listener.onClose(tab)));
        LayoutParams closeParams = new LayoutParams(dp(30), dp(30), Gravity.TOP | Gravity.END);
        closeParams.setMargins(0, dp(8), dp(8), 0);
        card.addView(close, closeParams);

        card.setOnClickListener(v -> listener.onSelect(tab));
        AddressBar.addSpringPress(card);

        // Cards rise in one after another and settle with a small bounce.
        card.setAlpha(0f);
        card.setTranslationY(dp(18));
        card.animate().alpha(1f).translationY(0f).setStartDelay(40L * Math.min(index, 8))
                .setDuration(420).setInterpolator(new OvershootInterpolator(1.6f));
        return card;
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
