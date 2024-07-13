package candybar.lib.helpers;

import static com.danimahardhika.android.helpers.core.FileHelper.getUriFromFile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.danimahardhika.android.helpers.core.utils.LogUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.io.IOException;

import candybar.lib.R;
import candybar.lib.activities.CandyBarMainActivity;
import candybar.lib.applications.CandyBarApplication;
import candybar.lib.fragments.dialog.IconPreviewFragment;
import candybar.lib.items.Icon;
import candybar.lib.utils.CandyBarGlideModule;

/*
 * CandyBar - Material Dashboard
 *
 * Copyright (c) 2014-2016 Dani Mahardhika
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

public class IconsHelper {
    public static void loadIcons(Context context, boolean sortIcons) throws Exception {
        // Load icons only if they are not loaded
        if (CandyBarMainActivity.sSections == null) {
            CandyBarMainActivity.sSections = getIconsList(context);

            for (Icon section : CandyBarMainActivity.sSections) {
                List<Icon> icons = section.getIcons();

                computeTitles(context, icons);

                if (sortIcons && context.getResources().getBoolean(R.bool.enable_icons_sort)) {
                    Collections.sort(icons, Icon.TitleComparator);
                    section.setIcons(icons);
                }
            }

            if (CandyBarApplication.getConfiguration().isShowTabAllIcons()) {
                List<Icon> icons = getTabAllIcons();
                CandyBarMainActivity.sSections.add(new Icon(
                        CandyBarApplication.getConfiguration().getTabAllIconsTitle(), icons));
            }
        }
    }

    @NonNull
    public static List<Icon> getIconsList(@NonNull Context context) throws Exception {
        XmlResourceParser parser = context.getResources().getXml(R.xml.drawable);
        int eventType = parser.getEventType();
        String sectionTitle = "";
        List<Icon> icons = new ArrayList<>();
        List<Icon> sections = new ArrayList<>();

        int count = 0;
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.getName().equals("category")) {
                    String title = parser.getAttributeValue(null, "title");
                    if (!sectionTitle.equals(title)) {
                        if (sectionTitle.length() > 0 && icons.size() > 0) {
                            count += icons.size();
                            sections.add(new Icon(sectionTitle, icons));
                        }
                    }
                    sectionTitle = title;
                    icons = new ArrayList<>();
                } else if (parser.getName().equals("item")) {
                    String drawableName = parser.getAttributeValue(null, "drawable");
                    String customName = parser.getAttributeValue(null, "name");
                    int id = DrawableHelper.getDrawableId(drawableName);
                    if (id > 0) {
                        icons.add(new Icon(drawableName, customName, id, context.getPackageName()));
                    }
                }
            }

            eventType = parser.next();
        }
        count += icons.size();
        CandyBarMainActivity.sIconsCount = count;
        if (!CandyBarApplication.getConfiguration().isAutomaticIconsCountEnabled() &&
                CandyBarApplication.getConfiguration().getCustomIconsCount() == 0) {
            CandyBarApplication.getConfiguration().setCustomIconsCount(count);
        }
        if (icons.size() > 0) {
            sections.add(new Icon(sectionTitle, icons));
        }
        parser.close();

        // Fetch icons from side app (com.candybar.dev.hydro_navy_blue)
        String sideAppPackageName = "com.candybar.dev.hydro_navy_blue";
        List<Icon> sideAppIcons = getIconsFromSideApp(context, sideAppPackageName);
        sections.addAll(sideAppIcons);

        return sections;
    }

    private static List<Icon> getIconsFromSideApp(Context context, String packageName) {
        List<Icon> icons = new ArrayList<>();
        PackageManager pm = context.getPackageManager();

        try {
            // Get the Resources object for the side app
            Resources sideAppResources = pm.getResourcesForApplication(packageName);

            // Accessing drawable resources
            int resourceId = sideAppResources.getIdentifier("drawable", "xml", packageName);
            XmlResourceParser parser = sideAppResources.getXml(resourceId);

            icons.addAll(parseIconsFromXml(parser, context, sideAppResources, packageName));

            // Log the size of the icons list
            System.out.println("SideAppIcons: " + icons.size());

            parser.close();
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException | XmlPullParserException | IOException e) {
            e.printStackTrace();
            // Handle exceptions while fetching icons from side app
        }

        return icons;
    }

    private static List<Icon> parseIconsFromXml(XmlPullParser parser, Context context, Resources sideAppResources, String packageName) throws XmlPullParserException, IOException {
        List<Icon> icons = new ArrayList<>();
        int eventType = parser.getEventType();
        String sectionTitle = "";
        List<Icon> sectionIcons = new ArrayList<>();
        File directory = context.getExternalFilesDir(null); // Example directory, adjust as needed

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.getName().equals("category")) {
                    sectionTitle = parser.getAttributeValue(null, "title");
                    sectionIcons = new ArrayList<>();
                } else if (parser.getName().equals("item")) {
                    String drawableName = parser.getAttributeValue(null, "drawable");
                    String customName = parser.getAttributeValue(null, "name");

                    // Log the drawable name
                    Log.d("DrawableName", "Drawable name: " + drawableName);

                    // Fetch the drawable ID from the side app's resources
                    int id = sideAppResources.getIdentifier(drawableName, "drawable", packageName);
                    if (id > 0) {
//                        // Get the drawable from resources
//                        Drawable drawable = sideAppResources.getDrawable(id);
//
//                        // Call saveIcon with the correct parameters
//                        String savedIconPath = saveIcon(new ArrayList<>(), directory, drawable, drawableName, new OnFileNameChange() {
//                            @Override
//                            public void call(String newName) {
//                                // Handle the callback if needed
//                            }
//                        });

//                        if (savedIconPath != null) {
                            sectionIcons.add(new Icon(drawableName, customName, id, packageName));
//                        }
                    }
                }
            } else if (eventType == XmlPullParser.END_TAG && parser.getName().equals("category")) {
                if (!sectionTitle.isEmpty() && !sectionIcons.isEmpty()) {
                    icons.add(new Icon(sectionTitle, sectionIcons));
                }
            }
            eventType = parser.next();
        }

        if (!sectionTitle.isEmpty() && !sectionIcons.isEmpty()) {
            icons.add(new Icon(sectionTitle, sectionIcons));
        }

        return icons;
    }

    public static List<Icon> getTabAllIcons() {
        List<Icon> icons = new ArrayList<>();
        String[] categories = CandyBarApplication.getConfiguration().getCategoryForTabAllIcons();

        if (categories != null && categories.length > 0) {
            for (String category : categories) {
                for (Icon section : CandyBarMainActivity.sSections) {
                    if (section.getTitle().equals(category)) {
                        icons.addAll(section.getIcons());
                        break;
                    }
                }
            }
        } else {
            for (Icon section : CandyBarMainActivity.sSections) {
                icons.addAll(section.getIcons());
            }
        }

        Collections.sort(icons, Icon.TitleComparator);
        return icons;
    }

    public static void computeTitles(@NonNull Context context, List<Icon> icons) {
        final boolean iconReplacer = context.getResources().getBoolean(R.bool.enable_icon_name_replacer);
        for (Icon icon : icons) {
            if (icon.getTitle() != null) {
                // Title is already computed, so continue
                continue;
            }
            if (icon.getCustomName() != null && !icon.getCustomName().equals("")) {
                icon.setTitle(icon.getCustomName());
            } else {
                icon.setTitle(replaceName(context, iconReplacer, icon.getDrawableName()));
            }
        }
    }

    public static String replaceName(@NonNull Context context, boolean iconReplacer, String name) {
        if (iconReplacer) {
            String[] replacer = context.getResources().getStringArray(R.array.icon_name_replacer);
            for (String replace : replacer) {
                String[] strings = replace.split(",");
                if (strings.length > 0)
                    name = name.replace(strings[0], strings.length > 1 ? strings[1] : "");
            }
        }
        name = name.replaceAll("_", " ");
        name = name.trim().replaceAll("\\s+", " ");
        return capitalizeWord(name);
    }

    public static String capitalizeWord(String str) {
        String[] words = str.split("\\s");
        StringBuilder capitalizeWord = new StringBuilder();
        for (String w : words) {
            String first = w.substring(0, 1);
            String afterfirst = w.substring(1);
            capitalizeWord.append(first.toUpperCase()).append(afterfirst).append(" ");
        }
        return capitalizeWord.toString().trim();
    }

    public static void selectIcon(@NonNull Context context, int action, Icon icon) {
        CandyBarApplication.getConfiguration().getAnalyticsHandler().logEvent(
                "click",
                new HashMap<String, Object>() {{
                    put("section", "icons");
                    put("action", "pick_icon");
                    put("item", icon.getDrawableName());
                }}
        );

        int resId = icon.getRes();
        String packageName = icon.getPackageName();

        String glideLoadUrl = "android.resource://" + packageName + "/" + resId;

        Log.d("loadurl", glideLoadUrl);

        if (action == IntentHelper.ICON_PICKER && CandyBarGlideModule.isValidContextForGlide(context)) {
            // This is where the icon gets picked when trying to change icon through the launcher, then opening the app
            Glide.with(context)
                    .asBitmap()
                    .load(Uri.parse(glideLoadUrl))
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .listener(new RequestListener<Bitmap>() {
                        public void handleResult(Bitmap resource) {
                            Intent intent = new Intent();
                            intent.putExtra("icon", resource);
                            ((AppCompatActivity) context).setResult(resource != null ?
                                    Activity.RESULT_OK : Activity.RESULT_CANCELED, intent);
                            ((AppCompatActivity) context).finish();
                        }

                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                            handleResult(null);
                            return true;
                        }

                        @Override
                        public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                            handleResult(resource);
                            return true;
                        }
                    })
                    .submit();
        } else if (action == IntentHelper.IMAGE_PICKER && CandyBarGlideModule.isValidContextForGlide(context)) {
            Glide.with(context)
                    .asBitmap()
                    .load(Uri.parse(glideLoadUrl))
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .listener(new RequestListener<Bitmap>() {
                        private void handleResult(Bitmap bitmap) {
                            Intent intent = new Intent();
                            if (bitmap != null) {
                                File file = new File(context.getCacheDir(), icon.getTitle() + ".png");
                                FileOutputStream outStream;
                                try {
                                    outStream = new FileOutputStream(file);
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream);
                                    outStream.flush();
                                    outStream.close();

                                    Uri uri = getUriFromFile(context, context.getPackageName(), file);
                                    if (uri == null) uri = Uri.fromFile(file);
                                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                                    intent.setData(uri);
                                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                } catch (Exception | OutOfMemoryError e) {
                                    LogUtil.e(Log.getStackTraceString(e));
                                }
                                intent.putExtra("return-data", false);
                            }
                            ((AppCompatActivity) context).setResult(bitmap != null ?
                                    Activity.RESULT_OK : Activity.RESULT_CANCELED, intent);
                            ((AppCompatActivity) context).finish();
                        }

                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                            handleResult(null);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                            handleResult(resource);
                            return true;
                        }
                    })
                    .submit();
        } else {
            IconPreviewFragment.showIconPreview(((AppCompatActivity) context)
                            .getSupportFragmentManager(),
                    icon.getTitle(), icon.getRes(), icon.getDrawableName(), icon.getPackageName());
        }
    }

    public interface OnFileNameChange {
        public void call(String newName);
    }

    @Nullable
    public static String saveIcon(List<String> files, File directory, Drawable drawable, String name, OnFileNameChange onFileNameChange) {
        Bitmap bitmap = DrawableHelper.toBitmap(drawable);
        assert bitmap != null;
        return saveBitmap(files, directory, bitmap, name, onFileNameChange);
    }

    public static String saveBitmap(List<String> files, File directory, Bitmap bitmap, String name, OnFileNameChange onFileNameChange) {
        String fileName = name + ".png";
        File file = new File(directory, fileName);
        try {
            Thread.sleep(2);

            if (files.contains(file.toString())) {
                fileName = fileName.replace(".png", "_" + System.currentTimeMillis() + ".png");
                file = new File(directory, fileName);
                onFileNameChange.call(fileName);
                LogUtil.e("Duplicate File name, Renamed: " + fileName);
            }

            FileOutputStream outStream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream);
            outStream.flush();
            outStream.close();
            return directory.toString() + "/" + fileName;
        } catch (Exception | OutOfMemoryError e) {
            LogUtil.e(Log.getStackTraceString(e));
        }
        return null;
    }
}
