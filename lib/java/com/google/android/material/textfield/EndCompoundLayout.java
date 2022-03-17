/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.material.textfield;

import com.google.android.material.R;

import static com.google.android.material.textfield.IconHelper.applyIconTint;
import static com.google.android.material.textfield.IconHelper.refreshIconDrawableState;
import static com.google.android.material.textfield.IconHelper.setIconOnClickListener;
import static com.google.android.material.textfield.IconHelper.setIconOnLongClickListener;
import static com.google.android.material.textfield.TextInputLayout.END_ICON_CLEAR_TEXT;
import static com.google.android.material.textfield.TextInputLayout.END_ICON_CUSTOM;
import static com.google.android.material.textfield.TextInputLayout.END_ICON_DROPDOWN_MENU;
import static com.google.android.material.textfield.TextInputLayout.END_ICON_NONE;
import static com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.appcompat.widget.TintTypedArray;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.MarginLayoutParamsCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;
import com.google.android.material.internal.CheckableImageButton;
import com.google.android.material.internal.ViewUtils;
import com.google.android.material.resources.MaterialResources;
import com.google.android.material.textfield.TextInputLayout.EndIconMode;
import com.google.android.material.textfield.TextInputLayout.OnEndIconChangedListener;
import java.util.LinkedHashSet;

/**
 * A compound layout that includes views that will be shown at the end of {@link TextInputLayout}
 * and their relevant rendering and presenting logic.
 */
@SuppressLint("ViewConstructor")
class EndCompoundLayout extends LinearLayout {
  final TextInputLayout textInputLayout;

  @NonNull private final FrameLayout endIconFrame;

  @NonNull private final CheckableImageButton errorIconView;
  private ColorStateList errorIconTintList;
  private PorterDuff.Mode errorIconTintMode;
  private OnLongClickListener errorIconOnLongClickListener;

  @NonNull private final CheckableImageButton endIconView;
  private final SparseArray<EndIconDelegate> endIconDelegates = new SparseArray<>();
  @EndIconMode private int endIconMode = END_ICON_NONE;
  private final LinkedHashSet<OnEndIconChangedListener> endIconChangedListeners =
      new LinkedHashSet<>();
  private ColorStateList endIconTintList;
  private PorterDuff.Mode endIconTintMode;
  private OnLongClickListener endIconOnLongClickListener;

  @Nullable private CharSequence suffixText;
  @NonNull private final TextView suffixTextView;

  private boolean hintExpanded;

  EndCompoundLayout(TextInputLayout textInputLayout, TintTypedArray a) {
    super(textInputLayout.getContext());

    this.textInputLayout = textInputLayout;

    setVisibility(GONE);
    setOrientation(HORIZONTAL);
    setLayoutParams(
        new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.END | Gravity.RIGHT));

    endIconFrame = new FrameLayout(getContext());
    endIconFrame.setVisibility(GONE);
    endIconFrame.setLayoutParams(
        new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

    LayoutInflater layoutInflater = LayoutInflater.from(getContext());
    errorIconView = createIconView(this, layoutInflater, R.id.text_input_error_icon);
    endIconView = createIconView(endIconFrame, layoutInflater, R.id.text_input_end_icon);

    suffixTextView = new AppCompatTextView(getContext());

    initErrorIconView(a);
    initEndIconDelegates(a);
    initSuffixTextView(a);

    endIconFrame.addView(endIconView);

    addView(suffixTextView);
    addView(endIconFrame);
    addView(errorIconView);
  }

  private CheckableImageButton createIconView(
      ViewGroup root, LayoutInflater inflater, @IdRes int id) {
    CheckableImageButton iconView =
        (CheckableImageButton) inflater.inflate(
            R.layout.design_text_input_end_icon, root, false);
    iconView.setId(id);
    if (MaterialResources.isFontScaleAtLeast1_3(getContext())) {
      ViewGroup.MarginLayoutParams lp =
          (ViewGroup.MarginLayoutParams) iconView.getLayoutParams();
      MarginLayoutParamsCompat.setMarginStart(lp, 0);
    }
    return iconView;
  }

  private void initErrorIconView(TintTypedArray a) {
    if (a.hasValue(R.styleable.TextInputLayout_errorIconTint)) {
      errorIconTintList =
          MaterialResources.getColorStateList(
              getContext(), a, R.styleable.TextInputLayout_errorIconTint);
    }
    if (a.hasValue(R.styleable.TextInputLayout_errorIconTintMode)) {
      errorIconTintMode =
          ViewUtils.parseTintMode(
              a.getInt(R.styleable.TextInputLayout_errorIconTintMode, -1), null);
    }
    if (a.hasValue(R.styleable.TextInputLayout_errorIconDrawable)) {
      setErrorIconDrawable(a.getDrawable(R.styleable.TextInputLayout_errorIconDrawable));
    }
    errorIconView.setContentDescription(
        getResources().getText(R.string.error_icon_content_description));
    ViewCompat.setImportantForAccessibility(
        errorIconView, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);
    errorIconView.setClickable(false);
    errorIconView.setPressable(false);
    errorIconView.setFocusable(false);
  }

  private void initEndIconDelegates(TintTypedArray a) {
    int endIconDrawableId = a.getResourceId(R.styleable.TextInputLayout_endIconDrawable, 0);
    endIconDelegates.append(END_ICON_CUSTOM, new CustomEndIconDelegate(this, endIconDrawableId));
    endIconDelegates.append(END_ICON_NONE, new NoEndIconDelegate(this));
    endIconDelegates.append(
        END_ICON_PASSWORD_TOGGLE,
        new PasswordToggleEndIconDelegate(
            this,
            endIconDrawableId == 0
                ? a.getResourceId(R.styleable.TextInputLayout_passwordToggleDrawable, 0)
                : endIconDrawableId));
    endIconDelegates.append(
        END_ICON_CLEAR_TEXT, new ClearTextEndIconDelegate(this, endIconDrawableId));
    endIconDelegates.append(
        END_ICON_DROPDOWN_MENU, new DropdownMenuEndIconDelegate(this, endIconDrawableId));
    // Set up the end icon if any.
    if (!a.hasValue(R.styleable.TextInputLayout_passwordToggleEnabled)) {
      // Default tint for any end icon or value specified by user
      if (a.hasValue(R.styleable.TextInputLayout_endIconTint)) {
        endIconTintList =
            MaterialResources.getColorStateList(
                getContext(), a, R.styleable.TextInputLayout_endIconTint);
      }
      // Default tint mode for any end icon or value specified by user
      if (a.hasValue(R.styleable.TextInputLayout_endIconTintMode)) {
        endIconTintMode =
            ViewUtils.parseTintMode(
                a.getInt(R.styleable.TextInputLayout_endIconTintMode, -1), null);
      }
    }
    if (a.hasValue(R.styleable.TextInputLayout_endIconMode)) {
      // Specific defaults depending on which end icon mode is set
      setEndIconMode(a.getInt(R.styleable.TextInputLayout_endIconMode, END_ICON_NONE));
      if (a.hasValue(R.styleable.TextInputLayout_endIconContentDescription)) {
        setEndIconContentDescription(
            a.getText(R.styleable.TextInputLayout_endIconContentDescription));
      }
      setEndIconCheckable(a.getBoolean(R.styleable.TextInputLayout_endIconCheckable, true));
    } else if (a.hasValue(R.styleable.TextInputLayout_passwordToggleEnabled)) {
      // Support for deprecated attributes related to the password toggle end icon
      if (a.hasValue(R.styleable.TextInputLayout_passwordToggleTint)) {
        endIconTintList =
            MaterialResources.getColorStateList(
                getContext(), a, R.styleable.TextInputLayout_passwordToggleTint);
      }
      if (a.hasValue(R.styleable.TextInputLayout_passwordToggleTintMode)) {
        endIconTintMode =
            ViewUtils.parseTintMode(
                a.getInt(R.styleable.TextInputLayout_passwordToggleTintMode, -1), null);
      }
      boolean passwordToggleEnabled =
          a.getBoolean(R.styleable.TextInputLayout_passwordToggleEnabled, false);
      setEndIconMode(passwordToggleEnabled ? END_ICON_PASSWORD_TOGGLE : END_ICON_NONE);
      setEndIconContentDescription(
          a.getText(R.styleable.TextInputLayout_passwordToggleContentDescription));
    }
  }

  private void initSuffixTextView(TintTypedArray a) {
    // Set up suffix view.
    suffixTextView.setVisibility(GONE);
    suffixTextView.setId(R.id.textinput_suffix_text);
    suffixTextView.setLayoutParams(
        new LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM));
    ViewCompat.setAccessibilityLiveRegion(
        suffixTextView, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);

    setSuffixTextAppearance(
        a.getResourceId(R.styleable.TextInputLayout_suffixTextAppearance, 0));
    if (a.hasValue(R.styleable.TextInputLayout_suffixTextColor)) {
      setSuffixTextColor(a.getColorStateList(R.styleable.TextInputLayout_suffixTextColor));
    }
    setSuffixText(a.getText(R.styleable.TextInputLayout_suffixText));
  }

  void setErrorIconDrawable(@DrawableRes int resId) {
    setErrorIconDrawable(resId != 0 ? AppCompatResources.getDrawable(getContext(), resId) : null);
    refreshErrorIconDrawableState();
  }

  void setErrorIconDrawable(@Nullable Drawable errorIconDrawable) {
    errorIconView.setImageDrawable(errorIconDrawable);
    updateErrorIconVisibility();
    applyIconTint(textInputLayout, errorIconView, errorIconTintList, errorIconTintMode);
  }

  Drawable getErrorIconDrawable() {
    return errorIconView.getDrawable();
  }

  void setErrorIconTintList(@Nullable ColorStateList errorIconTintList) {
    if (this.errorIconTintList != errorIconTintList) {
      this.errorIconTintList = errorIconTintList;
      applyIconTint(textInputLayout, errorIconView, errorIconTintList, errorIconTintMode);
    }
  }

  void setErrorIconTintMode(@Nullable PorterDuff.Mode errorIconTintMode) {
    if (this.errorIconTintMode != errorIconTintMode) {
      this.errorIconTintMode = errorIconTintMode;
      applyIconTint(textInputLayout, errorIconView, errorIconTintList, this.errorIconTintMode);
    }
  }

  void setErrorIconOnClickListener(@Nullable OnClickListener errorIconOnClickListener) {
    setIconOnClickListener(errorIconView, errorIconOnClickListener, errorIconOnLongClickListener);
  }

  CheckableImageButton getEndIconView() {
    return endIconView;
  }

  EndIconDelegate getEndIconDelegate() {
    EndIconDelegate endIconDelegate = endIconDelegates.get(endIconMode);
    return endIconDelegate != null ? endIconDelegate : endIconDelegates.get(END_ICON_NONE);
  }

  @EndIconMode
  int getEndIconMode() {
    return endIconMode;
  }

  void setEndIconMode(@EndIconMode int endIconMode) {
    if (this.endIconMode == endIconMode) {
      return;
    }
    int previousEndIconMode = this.endIconMode;
    this.endIconMode = endIconMode;
    dispatchOnEndIconChanged(previousEndIconMode);
    setEndIconVisible(endIconMode != END_ICON_NONE);
    if (getEndIconDelegate().isBoxBackgroundModeSupported(textInputLayout.getBoxBackgroundMode())) {
      getEndIconDelegate().initialize();
    } else {
      throw new IllegalStateException(
          "The current box background mode "
              + textInputLayout.getBoxBackgroundMode()
              + " is not supported by the end icon mode "
              + endIconMode);
    }
    applyIconTint(textInputLayout, endIconView, endIconTintList, endIconTintMode);
  }

  void setEndIconOnClickListener(@Nullable OnClickListener endIconOnClickListener) {
    setIconOnClickListener(endIconView, endIconOnClickListener, endIconOnLongClickListener);
  }

  void setEndIconOnLongClickListener(
      @Nullable OnLongClickListener endIconOnLongClickListener) {
    this.endIconOnLongClickListener = endIconOnLongClickListener;
    setIconOnLongClickListener(endIconView, endIconOnLongClickListener);
  }

  void setErrorIconOnLongClickListener(
      @Nullable OnLongClickListener errorIconOnLongClickListener) {
    this.errorIconOnLongClickListener = errorIconOnLongClickListener;
    setIconOnLongClickListener(errorIconView, errorIconOnLongClickListener);
  }

  void refreshErrorIconDrawableState() {
    refreshIconDrawableState(textInputLayout, errorIconView, errorIconTintList);
  }

  void setEndIconVisible(boolean visible) {
    if (isEndIconVisible() != visible) {
      endIconView.setVisibility(visible ? View.VISIBLE : View.GONE);
      updateEndLayoutVisibility();
      updateSuffixTextViewPadding();
      textInputLayout.updateDummyDrawables();
    }
  }

  boolean isEndIconVisible() {
    return endIconFrame.getVisibility() == VISIBLE && endIconView.getVisibility() == VISIBLE;
  }

  void setEndIconActivated(boolean endIconActivated) {
    endIconView.setActivated(endIconActivated);
  }

  void refreshEndIconDrawableState() {
    refreshIconDrawableState(textInputLayout, endIconView, endIconTintList);
  }

  void setEndIconCheckable(boolean endIconCheckable) {
    endIconView.setCheckable(endIconCheckable);
  }

  boolean isEndIconCheckable() {
    return endIconView.isCheckable();
  }

  boolean isEndIconChecked() {
    return hasEndIcon() && endIconView.isChecked();
  }

  void checkEndIcon() {
    endIconView.performClick();
    // Skip animation
    endIconView.jumpDrawablesToCurrentState();
  }

  void setEndIconDrawable(@DrawableRes int resId) {
    setEndIconDrawable(resId != 0 ? AppCompatResources.getDrawable(getContext(), resId) : null);
  }

  void setEndIconDrawable(@Nullable Drawable endIconDrawable) {
    endIconView.setImageDrawable(endIconDrawable);
    if (endIconDrawable != null) {
      applyIconTint(textInputLayout, endIconView, endIconTintList, endIconTintMode);
      refreshEndIconDrawableState();
    }
  }

  @Nullable
  Drawable getEndIconDrawable() {
    return endIconView.getDrawable();
  }

  void setEndIconContentDescription(@StringRes int resId) {
    setEndIconContentDescription(resId != 0 ? getResources().getText(resId) : null);
  }

  void setEndIconContentDescription(@Nullable CharSequence endIconContentDescription) {
    if (getEndIconContentDescription() != endIconContentDescription) {
      endIconView.setContentDescription(endIconContentDescription);
    }
  }

  @Nullable
  CharSequence getEndIconContentDescription() {
    return endIconView.getContentDescription();
  }

  void setEndIconTintList(@Nullable ColorStateList endIconTintList) {
    if (this.endIconTintList != endIconTintList) {
      this.endIconTintList = endIconTintList;
      applyIconTint(textInputLayout, endIconView, this.endIconTintList, endIconTintMode);
    }
  }

  void setEndIconTintMode(@Nullable PorterDuff.Mode endIconTintMode) {
    if (this.endIconTintMode != endIconTintMode) {
      this.endIconTintMode = endIconTintMode;
      applyIconTint(textInputLayout, endIconView, endIconTintList, this.endIconTintMode);
    }
  }

  void addOnEndIconChangedListener(@NonNull OnEndIconChangedListener listener) {
    endIconChangedListeners.add(listener);
  }

  void removeOnEndIconChangedListener(@NonNull OnEndIconChangedListener listener) {
    endIconChangedListeners.remove(listener);
  }

  void clearOnEndIconChangedListeners() {
    endIconChangedListeners.clear();
  }

  boolean hasEndIcon() {
    return endIconMode != END_ICON_NONE;
  }

  TextView getSuffixTextView() {
    return suffixTextView;
  }

  void setSuffixText(@Nullable CharSequence suffixText) {
    this.suffixText = TextUtils.isEmpty(suffixText) ? null : suffixText;
    suffixTextView.setText(suffixText);
    updateSuffixTextVisibility();
  }

  @Nullable
  CharSequence getSuffixText() {
    return suffixText;
  }

  void setSuffixTextAppearance(@StyleRes int suffixTextAppearance) {
    TextViewCompat.setTextAppearance(suffixTextView, suffixTextAppearance);
  }

  void setSuffixTextColor(@NonNull ColorStateList suffixTextColor) {
    suffixTextView.setTextColor(suffixTextColor);
  }

  @Nullable
  ColorStateList getSuffixTextColor() {
    return suffixTextView.getTextColors();
  }

  void setPasswordVisibilityToggleDrawable(@DrawableRes int resId) {
    setPasswordVisibilityToggleDrawable(
        resId != 0 ? AppCompatResources.getDrawable(getContext(), resId) : null);
  }

  void setPasswordVisibilityToggleDrawable(@Nullable Drawable icon) {
    endIconView.setImageDrawable(icon);
  }

  void setPasswordVisibilityToggleContentDescription(@StringRes int resId) {
    setPasswordVisibilityToggleContentDescription(
        resId != 0 ? getResources().getText(resId) : null);
  }

  void setPasswordVisibilityToggleContentDescription(@Nullable CharSequence description) {
    endIconView.setContentDescription(description);
  }

  @Nullable
  Drawable getPasswordVisibilityToggleDrawable() {
    return endIconView.getDrawable();
  }

  @Nullable
  CharSequence getPasswordVisibilityToggleContentDescription() {
    return endIconView.getContentDescription();
  }

  boolean isPasswordVisibilityToggleEnabled() {
    return endIconMode == END_ICON_PASSWORD_TOGGLE;
  }

  void setPasswordVisibilityToggleEnabled(boolean enabled) {
    if (enabled && endIconMode != END_ICON_PASSWORD_TOGGLE) {
      // Set password toggle end icon if it's not already set
      setEndIconMode(END_ICON_PASSWORD_TOGGLE);
    } else if (!enabled) {
      // Set end icon to null
      setEndIconMode(END_ICON_NONE);
    }
  }

  void setPasswordVisibilityToggleTintList(@Nullable ColorStateList tintList) {
    endIconTintList = tintList;
    applyIconTint(textInputLayout, endIconView, endIconTintList, endIconTintMode);
  }

  void setPasswordVisibilityToggleTintMode(@Nullable PorterDuff.Mode mode) {
    endIconTintMode = mode;
    applyIconTint(textInputLayout, endIconView, endIconTintList, endIconTintMode);
  }

  void togglePasswordVisibilityToggle(boolean shouldSkipAnimations) {
    if (endIconMode == END_ICON_PASSWORD_TOGGLE) {
      endIconView.performClick();
      if (shouldSkipAnimations) {
        endIconView.jumpDrawablesToCurrentState();
      }
    }
  }

  void onHintStateChanged(boolean hintExpanded) {
    this.hintExpanded = hintExpanded;
    updateSuffixTextVisibility();
  }

  void onTextInputBoxStateUpdated() {
    updateErrorIconVisibility();

    // Update icons tints
    refreshErrorIconDrawableState();
    refreshEndIconDrawableState();

    if (getEndIconDelegate().shouldTintIconOnError()) {
      tintEndIconOnError(textInputLayout.shouldShowError());
    }
  }

  private void updateSuffixTextVisibility() {
    int oldVisibility = suffixTextView.getVisibility();
    int newVisibility = (suffixText != null && !hintExpanded) ? VISIBLE : GONE;
    if (oldVisibility != newVisibility) {
      getEndIconDelegate().onSuffixVisibilityChanged(/* visible= */ newVisibility == VISIBLE);
    }
    updateEndLayoutVisibility();
    // Set visibility after updating end layout's visibility so screen readers correctly announce
    // when suffix text appears.
    suffixTextView.setVisibility(newVisibility);
    textInputLayout.updateDummyDrawables();
  }

  void updateSuffixTextViewPadding() {
    if (textInputLayout.editText == null) {
      return;
    }
    int endPadding =
        (isEndIconVisible() || isErrorIconVisible())
            ? 0 : ViewCompat.getPaddingEnd(textInputLayout.editText);
    ViewCompat.setPaddingRelative(
        suffixTextView,
        getContext()
            .getResources()
            .getDimensionPixelSize(R.dimen.material_input_text_to_prefix_suffix_padding),
        textInputLayout.editText.getPaddingTop(),
        endPadding,
        textInputLayout.editText.getPaddingBottom());
  }

  @Nullable
  CheckableImageButton getCurrentEndIconView() {
    if (isErrorIconVisible()) {
      return errorIconView;
    } else if (hasEndIcon() && isEndIconVisible()) {
      return endIconView;
    } else {
      return null;
    }
  }

  boolean isErrorIconVisible() {
    return errorIconView.getVisibility() == VISIBLE;
  }

  private void updateErrorIconVisibility() {
    boolean visible =
        getErrorIconDrawable() != null
            && textInputLayout.isErrorEnabled()
            && textInputLayout.shouldShowError();
    errorIconView.setVisibility(visible ? VISIBLE : GONE);
    updateEndLayoutVisibility();
    updateSuffixTextViewPadding();
    if (!hasEndIcon()) {
      textInputLayout.updateDummyDrawables();
    }
  }

  private void updateEndLayoutVisibility() {
    // Sync endIconFrame's visibility with the endIconView's.
    endIconFrame.setVisibility(
        (endIconView.getVisibility() == VISIBLE && !isErrorIconVisible()) ? VISIBLE : GONE);

    int suffixTextVisibility = (suffixText != null && !hintExpanded) ? VISIBLE : GONE;
    boolean shouldBeVisible =
        isEndIconVisible() || isErrorIconVisible() || suffixTextVisibility == VISIBLE;
    setVisibility(shouldBeVisible ? VISIBLE : GONE);
  }

  private void dispatchOnEndIconChanged(@EndIconMode int previousIcon) {
    for (OnEndIconChangedListener listener : endIconChangedListeners) {
      listener.onEndIconChanged(textInputLayout, previousIcon);
    }
  }

  private void tintEndIconOnError(boolean tintEndIconOnError) {
    if (tintEndIconOnError && getEndIconDrawable() != null) {
      // Setting the tint here instead of calling setEndIconTintList() in order to preserve and
      // restore the icon's original tint.
      Drawable endIconDrawable = DrawableCompat.wrap(getEndIconDrawable()).mutate();
      DrawableCompat.setTint(
          endIconDrawable, textInputLayout.getErrorCurrentTextColors());
      endIconView.setImageDrawable(endIconDrawable);
    } else {
      applyIconTint(textInputLayout, endIconView, endIconTintList, endIconTintMode);
    }
  }
}