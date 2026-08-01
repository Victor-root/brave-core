/* Copyright (c) 2022 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.theme;

import android.app.Activity;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.ColorInt;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

import org.chromium.base.BraveFeatureList;
import org.chromium.base.BravePreferenceKeys;
import org.chromium.build.annotations.NullMarked;
import org.chromium.build.annotations.Nullable;
import org.chromium.chrome.browser.flags.ChromeFeatureMap;
import org.chromium.chrome.browser.preferences.ChromeSharedPreferences;
import org.chromium.components.cached_flags.CachedFlag;

/** Controls Brave's runtime use of Material dynamic colors. */
@NullMarked
public final class BraveDynamicColors {
    // ChromeCachedFlags.<clinit> creates a singleton bytecode-redirected to BraveCachedFlags
    // before BraveCachedFlags' static fields are ready, so this CachedFlag must live separately.
    private static final CachedFlag sDynamicColorsFlag =
            new CachedFlag(
                    ChromeFeatureMap.getInstance(),
                    BraveFeatureList.BRAVE_ANDROID_DYNAMIC_COLORS,
                    false);

    private BraveDynamicColors() {}

    /** Returns the feature flag used for cached-flag registration and early-startup reads. */
    public static CachedFlag getCachedFlag() {
        return sDynamicColorsFlag;
    }

    /**
     * Returns whether dynamic colors are available for this app session.
     *
     * <p>Availability requires the cached feature flag to be enabled and Android 12 or later. It
     * does not include the user's preference; {@link #isDynamicColorsEnabled()} is the preferred
     * method for runtime behavior checks.
     */
    public static boolean isDynamicColorsAvailable() {
        return getCachedFlag().isEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    /**
     * Returns whether dynamic colors should be used at runtime.
     *
     * <p>This requires dynamic colors to be available and the user preference to be enabled. The
     * preference defaults to enabled when it has not been set.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    public static boolean isDynamicColorsEnabled() {
        return isDynamicColorsAvailable() && isDynamicColorsUserEnabled();
    }

    /** Returns the persisted user preference, which defaults to enabled when unset. */
    private static boolean isDynamicColorsUserEnabled() {
        return ChromeSharedPreferences.getInstance()
                .readBoolean(BravePreferenceKeys.BRAVE_ANDROID_DYNAMIC_COLORS_ENABLED, true);
    }

    /**
     * Applies Material dynamic colors when they are available and enabled by the user preference.
     *
     * <p>This is a no-op otherwise.
     */
    public static void applyToActivityIfAvailable(Activity activity) {
        if (!isDynamicColorsEnabled()) {
            return;
        }

        Integer wallpaperColor = getWallpaperFallbackColor(activity);
        if (wallpaperColor == null) {
            DynamicColors.applyToActivityIfAvailable(activity);
            return;
        }

        DynamicColors.applyToActivityIfAvailable(
                activity,
                new DynamicColorsOptions.Builder().setContentBasedSource(wallpaperColor).build());
    }

    /**
     * Applies Material dynamic colors with the supplied options when they are available and enabled
     * by the user preference.
     *
     * <p>This is a no-op otherwise.
     */
    public static void applyToActivityIfAvailable(
            Activity activity, DynamicColorsOptions dynamicColorsOptions) {
        if (!isDynamicColorsEnabled()) {
            return;
        }

        DynamicColors.applyToActivityIfAvailable(activity, dynamicColorsOptions);
    }

    /**
     * Returns the color to seed the palette with, or null to let the system palette be used as is.
     *
     * <p>Since Android 13 Material considers dynamic colors available on every device, but some
     * vendor skins never install the wallpaper derived palette the platform defines, which leaves
     * the interface on the framework accent whatever the wallpaper is. Those devices still let the
     * platform extract the wallpaper colors, so they are used as a seed instead.
     */
    @ColorInt
    @RequiresApi(Build.VERSION_CODES.S)
    private static @Nullable Integer getWallpaperFallbackColor(Activity activity) {
        if (hasSystemDynamicPalette(activity)) {
            return null;
        }

        // Reading these colors requires no permission and never touches the wallpaper image: the
        // platform extracts them when the wallpaper is set.
        WallpaperColors wallpaperColors =
                WallpaperManager.getInstance(activity)
                        .getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
        return wallpaperColors == null ? null : wallpaperColors.getPrimaryColor().toArgb();
    }

    /**
     * Returns whether the system installed a dynamic palette on top of the framework defaults.
     *
     * <p>{@link Resources#getSystem()} only resolves the framework resources and the overlays baked
     * in at boot, while a context also resolves the palette overlay that the system theme engine
     * installs for the current user. Both resolving to the same accent means that no such overlay
     * is in place.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    static boolean hasSystemDynamicPalette(Context context) {
        return context.getColor(android.R.color.system_accent1_500)
                != Resources.getSystem().getColor(android.R.color.system_accent1_500, null);
    }
}
