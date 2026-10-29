CueAim Pro launcher icons

Copy the included app folder into the root of your Android project and merge with existing files.
This adds ic_launcher.png and ic_launcher_round.png to the Android mipmap density folders.
Then set these attributes inside the <application> tag in app/src/main/AndroidManifest.xml:
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"

Do not replace your existing manifest. Only add the two attributes to the existing <application> tag.
