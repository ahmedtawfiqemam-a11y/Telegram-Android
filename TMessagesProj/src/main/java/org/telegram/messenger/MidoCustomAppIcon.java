package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.LauncherIconController;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collections;

/**
 * Allows any user (without Telegram Premium) to pick/remove a custom image
 * from their device gallery for the app icon and pin it cleanly to the Home Screen.
 */
public class MidoCustomAppIcon {

    public static final int REQ_CODE_PICK_APP_ICON = 4901;
    private static final String FILE_NAME = "mido_custom_app_icon.png";
    private static final String SHORTCUT_ID = "mido_custom_app_shortcut";

    private static Bitmap cachedBitmap;
    private static String cachedPath;

    public static File getIconFile() {
        if (ApplicationLoader.applicationContext == null) {
            return null;
        }
        return new File(ApplicationLoader.applicationContext.getFilesDir(), FILE_NAME);
    }

    public static boolean hasCustomIcon() {
        if (TextUtils.isEmpty(MidoConfig.customAppIconPath)) {
            return false;
        }
        File f = new File(MidoConfig.customAppIconPath);
        return f.exists() && f.length() > 0;
    }

    public static Bitmap getCustomIconBitmap() {
        if (!hasCustomIcon()) {
            return null;
        }
        if (cachedBitmap != null && !cachedBitmap.isRecycled() && MidoConfig.customAppIconPath.equals(cachedPath)) {
            return cachedBitmap;
        }
        try {
            Bitmap bmp = BitmapFactory.decodeFile(MidoConfig.customAppIconPath);
            cachedBitmap = bmp;
            cachedPath = MidoConfig.customAppIconPath;
            return bmp;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    public static void openImagePicker(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            fragment.startActivityForResult(Intent.createChooser(intent, "اختر صورة للتطبيق"), REQ_CODE_PICK_APP_ICON);
        } catch (Throwable e) {
            FileLog.e(e);
            BulletinFactory.of(fragment).createErrorBulletin("تعذر فتح معرض الصور").show();
        }
    }

    public static boolean handleActivityResult(BaseFragment fragment, int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_CODE_PICK_APP_ICON) {
            return false;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null || fragment == null || fragment.getParentActivity() == null) {
            return true;
        }
        Uri uri = data.getData();
        Context ctx = fragment.getParentActivity();
        Utilities.globalQueue.postRunnable(() -> {
            Bitmap processed = decodeAndCropSquare(ctx, uri, 256);
            if (processed == null) {
                AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(fragment).createErrorBulletin("تعذر قراءة الصورة المختارة").show());
                return;
            }
            File outFile = getIconFile();
            if (outFile == null) {
                return;
            }
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                processed.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.flush();
            } catch (Throwable e) {
                FileLog.e(e);
                return;
            }

            cachedBitmap = processed;
            cachedPath = outFile.getAbsolutePath();
            AndroidUtilities.runOnUIThread(() -> {
                MidoConfig.setCustomAppIconPath(outFile.getAbsolutePath());
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.midoCustomAppIconChanged);
                pinCustomShortcut(ctx, processed);
                BulletinFactory.of(fragment).createSuccessBulletin("تم تعيين صورتك المخصصة للتطبيق وتجهيز أيقونة الشاشة الرئيسية 🖼️").show();
            });
        });
        return true;
    }

    public static void pinCustomShortcutToHome(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) return;
        Bitmap bmp = getCustomIconBitmap();
        if (bmp == null) {
            openImagePicker(fragment);
            return;
        }
        pinCustomShortcut(fragment.getParentActivity(), bmp);
        BulletinFactory.of(fragment).createSuccessBulletin("تم إرسال أيقونة التطبيق المخصصة للشاشة الرئيسية 📲").show();
    }

    public static void showManageCustomIconDialog(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) return;
        if (!hasCustomIcon()) {
            openImagePicker(fragment);
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle("صورة التطبيق المخصصة 🖼️");
        builder.setItems(new CharSequence[]{
                "تغيير الصورة من جهازك 🖼️",
                "تثبيت الأيقونة على الشاشة الرئيسية 📲",
                "إزالة الصورة والرجوع للافتراضي 🗑"
        }, (d, which) -> {
            if (which == 0) {
                openImagePicker(fragment);
            } else if (which == 1) {
                pinCustomShortcutToHome(fragment);
            } else {
                removeCustomIcon(fragment);
            }
        });
        fragment.showDialog(builder.create());
    }

    public static void removeCustomIcon(BaseFragment fragment) {
        File f = getIconFile();
        if (f != null && f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        cachedBitmap = null;
        cachedPath = "";
        MidoConfig.setCustomAppIconPath("");
        LauncherIconController.setIcon(LauncherIconController.LauncherIcon.DEFAULT);

        Context ctx = ApplicationLoader.applicationContext;
        if (ctx != null && Build.VERSION.SDK_INT >= 25) {
            try {
                ShortcutManager sm = ctx.getSystemService(ShortcutManager.class);
                if (sm != null) {
                    sm.disableShortcuts(Collections.singletonList(SHORTCUT_ID));
                }
            } catch (Throwable ignore) {
            }
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.midoCustomAppIconChanged);
        if (fragment != null) {
            BulletinFactory.of(fragment).createSuccessBulletin("تمت إزالة الصورة المخصصة والعودة لأيقونة التطبيق الافتراضية").show();
        }
    }

    private static void pinCustomShortcut(Context ctx, Bitmap bmp) {
        if (ctx == null || bmp == null) return;
        try {
            Intent launchIntent = new Intent(ctx, LaunchActivity.class);
            launchIntent.setAction(Intent.ACTION_MAIN);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ShortcutManager sm = ctx.getSystemService(ShortcutManager.class);
                if (sm != null && sm.isRequestPinShortcutSupported()) {
                    ShortcutInfo shortcut = new ShortcutInfo.Builder(ctx, SHORTCUT_ID)
                            .setShortLabel("Mido Telegram")
                            .setLongLabel("Mido Telegram")
                            .setIcon(Icon.createWithBitmap(bmp))
                            .setIntent(launchIntent)
                            .build();
                    sm.requestPinShortcut(shortcut, null);
                }
            } else {
                Intent addIntent = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent);
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, "Mido Telegram");
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, Bitmap.createScaledBitmap(bmp, 128, 128, true));
                ctx.sendBroadcast(addIntent);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static Bitmap decodeAndCropSquare(Context ctx, Uri uri, int targetSize) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream is1 = ctx.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(is1, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }
            int minDim = Math.min(bounds.outWidth, bounds.outHeight);
            int sampleSize = 1;
            while (minDim / (sampleSize * 2) >= targetSize) {
                sampleSize *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize;
            Bitmap decoded;
            try (InputStream is2 = ctx.getContentResolver().openInputStream(uri)) {
                decoded = BitmapFactory.decodeStream(is2, null, opts);
            }
            if (decoded == null) {
                return null;
            }
            int size = Math.min(decoded.getWidth(), decoded.getHeight());
            int x = (decoded.getWidth() - size) / 2;
            int y = (decoded.getHeight() - size) / 2;
            Bitmap square = Bitmap.createBitmap(decoded, x, y, size, size);
            Bitmap scaled = Bitmap.createScaledBitmap(square, targetSize, targetSize, true);

            Bitmap output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setShader(new BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            float radius = targetSize * 0.22f;
            canvas.drawRoundRect(new RectF(0, 0, targetSize, targetSize), radius, radius, paint);

            if (square != decoded) square.recycle();
            if (scaled != square) scaled.recycle();
            decoded.recycle();
            return output;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }
}
