package com.nucleanyt.privatemuch;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Full-screen settings: which search engine (and AI) to use. */
final class SettingsView extends FrameLayout {

    interface Listener {
        void onEngine(Engine engine);
        void onDismiss();
    }

    private static final int INK = Color.parseColor("#0B1012");
    private static final int SURFACE = Color.parseColor("#131B1E");
    private static final int LINE = Color.parseColor("#24333A");
    private static final int TEXT = Color.parseColor("#E2EBE9");
    private static final int MUTED = Color.parseColor("#8FA29F");
    private static final int TEAL = Color.parseColor("#35D0BF");

    private final Listener listener;
    private final List<Option> options = new ArrayList<>();

    /** One choice card: its outline and radio dot change when it's picked. */
    private static final class Option {
        Engine engine;
        GradientDrawable shape;
        View dot;
    }

    SettingsView(Context context, Engine selected, Listener listener) {
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
        back.setContentDescription("Close settings");
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        back.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#4035D0BF")), null, mask));
        back.setOnClickListener(v -> listener.onDismiss());
        AddressBar.addSpringPress(back);
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = new TextView(context);
        title.setText("Settings");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(dp(4), 0, 0, 0);
        header.addView(title);
        column.addView(header);

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(8), dp(18), dp(28));
        body.setClipChildren(false); // a card's bounce may reach past its edge for a moment; never cut it off

        body.addView(label("Search engine"));
        int i = 0;
        for (Engine engine : Engine.values()) {
            View card = optionCard(engine, engine == selected, i++);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, dp(10));
            body.addView(card, params);
        }

        TextView note = new TextView(context);
        note.setText("Your search engine and your bookmarks (just their addresses, as shortcuts) are the only things PrivateMuch remembers. Tabs, pages, cookies and site data are still wiped every time you close the app.");
        note.setTextColor(MUTED);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        note.setLineSpacing(0, 1.2f);
        note.setPadding(dp(4), dp(10), dp(4), dp(18));
        body.addView(note);

        TextView version = new TextView(context);
        version.setText("PrivateMuch " + versionName(context));
        version.setTextColor(Color.parseColor("#5B6D6A"));
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        version.setPadding(dp(4), 0, 0, 0);
        body.addView(version);

        ScrollView scroll = new ScrollView(context);
        scroll.addView(body);
        column.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        addView(column, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Slides in from the right with a soft settle.
        setAlpha(0f);
        setTranslationX(dp(40));
        animate().alpha(1f).translationX(0f).setDuration(340).setInterpolator(new OvershootInterpolator(1.1f));
    }

    void dismiss(Runnable after) {
        animate().alpha(0f).translationX(dp(40)).setDuration(200).withEndAction(after);
    }

    private View optionCard(Engine engine, boolean isSelected, int index) {
        Context context = getContext();
        Option option = new Option();
        option.engine = engine;

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        option.shape = new GradientDrawable();
        option.shape.setColor(SURFACE);
        option.shape.setCornerRadius(dp(16));
        card.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#3335D0BF")), option.shape, null));

        // Radio: a ring with a teal dot that pops in when picked.
        FrameLayout radio = new FrameLayout(context);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(dp(2), MUTED);
        radio.setBackground(ring);
        option.dot = new View(context);
        GradientDrawable dotShape = new GradientDrawable();
        dotShape.setShape(GradientDrawable.OVAL);
        dotShape.setColor(TEAL);
        option.dot.setBackground(dotShape);
        radio.addView(option.dot, new LayoutParams(dp(10), dp(10), Gravity.CENTER));
        card.addView(radio, new LinearLayout.LayoutParams(dp(22), dp(22)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(14), 0, 0, 0);
        TextView name = new TextView(context);
        name.setText(engine.title);
        name.setTextColor(TEXT);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        texts.addView(name);
        TextView description = new TextView(context);
        description.setText(engine.description);
        description.setTextColor(MUTED);
        description.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        description.setLineSpacing(0, 1.15f);
        description.setPadding(0, dp(2), 0, 0);
        texts.addView(description);
        card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        options.add(option);
        show(option, isSelected, false);
        card.setOnClickListener(v -> {
            for (Option o : options) show(o, o == option, true);
            listener.onEngine(engine);
        });
        AddressBar.addSpringPress(card, 0.97f);

        card.setAlpha(0f);
        card.setTranslationY(dp(14));
        card.animate().alpha(1f).translationY(0f).setStartDelay(60L * index).setDuration(420)
                .setInterpolator(new OvershootInterpolator(1.5f));
        return card;
    }

    private void show(Option option, boolean picked, boolean animate) {
        option.shape.setStroke(dp(2), picked ? TEAL : LINE); // same width either way, only the color changes
        float scale = picked ? 1f : 0f;
        if (animate) {
            option.dot.animate().scaleX(scale).scaleY(scale).setDuration(380).setInterpolator(new OvershootInterpolator(3f));
        } else {
            option.dot.setScaleX(scale);
            option.dot.setScaleY(scale);
        }
    }

    private TextView label(String text) {
        TextView label = new TextView(getContext());
        label.setText(text);
        label.setTextColor(MUTED);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setPadding(dp(4), dp(8), 0, dp(10));
        return label;
    }

    private static String versionName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
