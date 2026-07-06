#!/bin/bash

echo "Signing Release keys"
git clone https://github.com/Lunaris-AOSP/vendor_lunaris-priv_keys.git vendor/lunaris-priv/keys

echo "Unlock 4K60FPS Cam Recording"
cd packages/apps/Aperture
git fetch https://github.com/Adarsh0127-Elite/android_packages_apps_Aperture.git
git cherry-pick 9509277efc852ad8bdcce204e0d9cfe104b6d190
cd ../../..

echo "Fixup! L2CAP and A2DP offload coex mechanism for MTK"
cd packages/modules/Bluetooth
git fetch https://github.com/Adarsh0127-Elite/packages_modules_Bluetooth.git
git cherry-pick 92549f3b3ed77c98ca4bc7316bfd2f0662ac11f9
cd ../../..
