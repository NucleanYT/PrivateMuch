package com.nucleanyt.privatemuch;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The top bar shown while a website is open: Home, then a rounded address field you can type into
 * (a web address or a search), with a lock for secure sites, a reload/clear button and a thin loading line.
 */
final class AddressBar extends LinearLayout {

    interface Listener {
        void onHome();
        void onSubmit(String typed);
        void onReload();
        void onEditing(boolean editing);
    }

    private static final int INK = Color.parseColor("#0B1012");
    private static final int SURFACE = Color.parseColor("#131B1E");
    private static final int LINE = Color.parseColor("#24333A");
    private static final int TEXT = Color.parseColor("#E2EBE9");
    private static final int FAINT = Color.parseColor("#5B6D6A");
    private static final int TEAL = Color.parseColor("#35D0BF");

    private final Listener listener;
    private final LinearLayout pill;
    private final GradientDrawable pillShape = new GradientDrawable();
    private final ImageView lock;
    private final EditText field;
    private final ImageButton action;
    private final View progress;
    private ValueAnimator strokeAnimation;
    private final int strokeWidth; // never changes, so a thicker outline can't spill past the edge and get cut off
    private String url = "";

    AddressBar(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setOrientation(VERTICAL);
        setBackgroundColor(INK);
        // The bar itself can hold focus, so leaving the field doesn't hand focus straight back to it.
        setFocusable(true);
        setFocusableInTouchMode(true);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), 0, dp(10), 0);

        ImageButton home = iconButton(R.drawable.ic_home, "Home");
        home.setOnClickListener(v -> listener.onHome());
        row.addView(home, new LayoutParams(dp(48), dp(48)));

        pill = new LinearLayout(context);
        pill.setOrientation(HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(12), 0, dp(2), 0);
        strokeWidth = Math.round(1.5f * getResources().getDisplayMetrics().density);
        pillShape.setColor(SURFACE);
        pillShape.setCornerRadius(dp(14));
        pillShape.setStroke(strokeWidth, LINE);
        pill.setBackground(pillShape);

        lock = new ImageView(context);
        lock.setImageResource(R.drawable.ic_lock);
        pill.addView(lock, new LayoutParams(dp(16), dp(16)));

        field = new EditText(context);
        field.setBackground(null);
        field.setSingleLine(true);
        field.setTextColor(TEXT);
        field.setHintTextColor(FAINT);
        field.setHint("Search or type a web address");
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        field.setPadding(dp(8), 0, dp(4), 0);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        // Go key, no full-screen keyboard editor, and the keyboard must not learn what is typed here.
        field.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }
        field.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId != EditorInfo.IME_ACTION_GO && !enter) return false;
            listener.onSubmit(field.getText().toString());
            return true;
        });
        field.setOnFocusChangeListener((v, focused) -> showEditing(focused));
        pill.addView(field, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        action = iconButton(R.drawable.ic_reload, "Reload");
        action.setOnClickListener(v -> {
            if (field.hasFocus()) {
                field.setText("");
            } else {
                listener.onReload();
            }
        });
        pill.addView(action, new LayoutParams(dp(36), dp(36)));

        LayoutParams pillParams = new LayoutParams(0, dp(42), 1f);
        pillParams.setMarginStart(dp(2));
        row.addView(pill, pillParams);
        addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        // A hairline under the bar, with the teal loading line drawn over it.
        FrameLayout edge = new FrameLayout(context);
        View hairline = new View(context);
        hairline.setBackgroundColor(Color.parseColor("#1A2427"));
        edge.addView(hairline, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.BOTTOM));
        progress = new View(context);
        progress.setBackgroundColor(TEAL);
        progress.setPivotX(0);
        progress.setScaleX(0);
        progress.setAlpha(0);
        edge.addView(progress, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2), Gravity.BOTTOM));
        addView(edge, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));
    }

    /** Shows the site's name (or the full address while typing). */
    void setUrl(String url) {
        this.url = url == null ? "" : url;
        boolean secure = this.url.startsWith("https://");
        lock.setImageResource(secure ? R.drawable.ic_lock : R.drawable.ic_lock_open);
        lock.setContentDescription(secure ? "Secure connection" : "Not secure");
        if (!field.hasFocus()) field.setText(displayName(this.url));
    }

    void setProgress(int percent) {
        if (percent < 100) {
            if (progress.getAlpha() == 0f) progress.setScaleX(0);
            progress.animate().alpha(1f).scaleX(Math.max(0.08f, percent / 100f))
                    .setDuration(220).setInterpolator(new DecelerateInterpolator());
        } else {
            progress.animate().scaleX(1f).setDuration(160)
                    .withEndAction(() -> progress.animate().alpha(0f).setDuration(260));
        }
    }

    boolean isEditing() {
        return field.hasFocus();
    }

    void stopEditing() {
        field.clearFocus();
        InputMethodManager keyboard = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(field.getWindowToken(), 0);
    }

    private void showEditing(boolean editing) {
        listener.onEditing(editing);
        field.setText(editing ? url : displayName(url));
        if (editing) field.post(field::selectAll);
        action.setImageResource(editing ? R.drawable.ic_clear : R.drawable.ic_reload);
        action.setContentDescription(editing ? "Clear" : "Reload");
        lock.animate().alpha(editing ? 0.4f : 1f).setDuration(200);
        // The field lifts a little and its outline turns teal, then settles back with a gentle bounce.
        pill.animate().scaleX(editing ? 1.02f : 1f).scaleY(editing ? 1.02f : 1f)
                .setDuration(420).setInterpolator(new OvershootInterpolator(2f));
        if (strokeAnimation != null) strokeAnimation.cancel();
        int from = editing ? LINE : TEAL;
        int to = editing ? TEAL : LINE;
        strokeAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        strokeAnimation.setDuration(220);
        strokeAnimation.addUpdateListener(a -> pillShape.setStroke(strokeWidth, (int) a.getAnimatedValue()));
        strokeAnimation.start();
    }

    private ImageButton iconButton(int icon, String description) {
        ImageButton b = new ImageButton(getContext());
        b.setImageResource(icon);
        b.setContentDescription(description);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#4035D0BF")), null, circle()));
        addSpringPress(b);
        return b;
    }

    private static GradientDrawable circle() {
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        return mask;
    }

    /**
     * The tabs button: a rounded square with the number of open tabs, like most phone browsers.
     * The home page uses the filled version so it stands out over the page.
     */
    static TextView tabsButton(Context context, boolean filled) {
        TextView b = new TextView(context);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(TEXT);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        b.setText("1");
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable square = new GradientDrawable();
        square.setCornerRadius(9 * density);
        square.setColor(filled ? Color.parseColor("#E6131B1E") : Color.TRANSPARENT);
        square.setStroke(Math.round(1.8f * density), filled ? LINE : Color.parseColor("#8FA29F"));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#4035D0BF")), square, null));
        addSpringPress(b);
        return b;
    }

    /** "duckduckgo.com" instead of the full address, like most phone browsers. */
    static String displayName(String url) {
        if (url == null || url.isEmpty()) return "";
        String host = Uri.parse(url).getHost();
        if (host == null) return url;
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    /** A gentle squish when pressed and a bouncy return, used by every button in the app. */
    static void addSpringPress(View view) {
        addSpringPress(view, 0.9f);
    }

    /** Big cards squish much less, so their bounce back stays inside the screen. */
    static void addSpringPress(View view, float pressedScale) {
        view.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(pressedScale).scaleY(pressedScale).setDuration(120).setInterpolator(new DecelerateInterpolator());
            } else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(450).setInterpolator(new OvershootInterpolator(3f));
            }
            return false;
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
