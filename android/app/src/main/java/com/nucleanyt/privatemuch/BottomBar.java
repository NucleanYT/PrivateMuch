package com.nucleanyt.privatemuch;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The bar along the bottom while a website is open: Back, Forward, Tabs (with the count) and New tab. */
final class BottomBar extends LinearLayout {

    interface Listener {
        void onBack();
        void onForward();
        void onTabs();
        void onNewTab();
    }

    private final ImageButton back;
    private final ImageButton forward;
    private final TextView tabs;

    BottomBar(Context context, Listener listener) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(Color.parseColor("#0B1012"));

        View hairline = new View(context);
        hairline.setBackgroundColor(Color.parseColor("#1A2427"));
        addView(hairline, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        back = iconButton(R.drawable.ic_back, "Back");
        back.setOnClickListener(v -> listener.onBack());
        row.addView(slot(back, dp(48)), slotParams());

        forward = iconButton(R.drawable.ic_forward, "Forward");
        forward.setOnClickListener(v -> listener.onForward());
        row.addView(slot(forward, dp(48)), slotParams());

        tabs = AddressBar.tabsButton(context, false);
        tabs.setOnClickListener(v -> listener.onTabs());
        row.addView(slot(tabs, dp(30)), slotParams());

        ImageButton newTab = iconButton(R.drawable.ic_new_tab, "New tab");
        newTab.setOnClickListener(v -> listener.onNewTab());
        row.addView(slot(newTab, dp(48)), slotParams());

        addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
    }

    /** Back and Forward fade out when there's nowhere to go. */
    void setNavigation(boolean canGoBack, boolean canGoForward) {
        setAvailable(back, canGoBack);
        setAvailable(forward, canGoForward);
    }

    void setTabCount(int count) {
        tabs.setText(count > 99 ? ":D" : String.valueOf(count));
        tabs.setContentDescription(count == 1 ? "1 tab open" : count + " tabs open");
    }

    private static void setAvailable(View button, boolean available) {
        button.setEnabled(available);
        button.animate().alpha(available ? 1f : 0.3f).setDuration(200);
    }

    /** Each button sits centered in an equal share of the bar. */
    private FrameLayout slot(View button, int size) {
        FrameLayout slot = new FrameLayout(getContext());
        slot.addView(button, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        return slot;
    }

    private static LayoutParams slotParams() {
        return new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    private ImageButton iconButton(int icon, String description) {
        ImageButton b = new ImageButton(getContext());
        b.setImageResource(icon);
        b.setContentDescription(description);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#4035D0BF")), null, mask));
        AddressBar.addSpringPress(b);
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
