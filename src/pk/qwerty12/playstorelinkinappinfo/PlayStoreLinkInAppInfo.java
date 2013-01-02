package pk.qwerty12.playstorelinkinappinfo;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class PlayStoreLinkInAppInfo implements IXposedHookLoadPackage {
	
	private final static String PACKAGE_SETTINGS = "com.android.settings";
	
	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		if (lpparam.packageName.equals(PACKAGE_SETTINGS)) {
			try {
				final Class<?> classInstalledAppDetails = XposedHelpers.findClass(PACKAGE_SETTINGS + ".applications.InstalledAppDetails", lpparam.classLoader);

				XposedHelpers.findAndHookMethod(classInstalledAppDetails, "onCreateView", LayoutInflater.class, ViewGroup.class, Bundle.class, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
						View mNotificationSwitch = (View) XposedHelpers.getObjectField(param.thisObject, "mNotificationSwitch");
						View mUninstallButton = (View) XposedHelpers.getObjectField(param.thisObject, "mUninstallButton");
						ViewGroup viewGroup = (ViewGroup) mNotificationSwitch.getParent();
						final Context context = viewGroup.getContext();

						Button playStoreButton = new Button(context);
						playStoreButton.setText("Play Store");
						playStoreButton.setVisibility(View.GONE);
						playStoreButton.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								//Taken from Eric's answer at http://stackoverflow.com/questions/10922762/open-link-of-google-play-store-in-mobile-version-android
								final String packageName = (String) v.getTag();
								if (packageName != null)
									context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id="+packageName)));
							}
						});
						
						viewGroup.addView(playStoreButton, viewGroup.indexOfChild(mNotificationSwitch) + 1, mUninstallButton.getLayoutParams());
						
						XposedHelpers.setAdditionalInstanceField(param.thisObject, "playStoreButton", playStoreButton);
					}
				});

				XposedHelpers.findAndHookMethod(classInstalledAppDetails, "refreshUi", new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						Button playStoreButton = (Button) XposedHelpers.getAdditionalInstanceField(param.thisObject, "playStoreButton");
						if ((!((Boolean) param.getResult())) || playStoreButton != null && playStoreButton.getVisibility() == View.VISIBLE)
							return;

						final String packageName = ((ApplicationInfo) XposedHelpers.getObjectField(XposedHelpers.getObjectField(param.thisObject, "mAppEntry"), "info")).packageName;
						PackageManager pm = (PackageManager) XposedHelpers.getObjectField(param.thisObject, "mPm");

						boolean isSystemPackage = ((Boolean) XposedHelpers.callMethod(param.thisObject, "isThisASystemPackage"));
						final String InstallerPackageName = pm.getInstallerPackageName(packageName);

						if ((!isSystemPackage && InstallerPackageName != null) && (InstallerPackageName.equals("com.android.vending") || InstallerPackageName.contains("google"))) {
								playStoreButton.setTag(packageName);
								playStoreButton.setVisibility(View.VISIBLE);
						}
					}
				});
			} catch (Throwable t) { XposedBridge.log(t); }
		}
	}

}
