#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

# Inherit from those products. Most specific first.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)

# Inherit from device makefile.
$(call inherit-product, device/xiaomi/duchamp/device.mk)

# Inherit some common LineageOS stuff.
$(call inherit-product, vendor/lineage/config/common_full_phone.mk)

PRODUCT_NAME := lineage_duchamp
PRODUCT_DEVICE := duchamp
PRODUCT_MANUFACTURER := Xiaomi
PRODUCT_BRAND := POCO
PRODUCT_MODEL := 2311DRK48G
PRODUCT_SYSTEM_NAME := duchamp_global

PRODUCT_CHARACTERISTICS := nosdcard
PRODUCT_GMS_CLIENTID_BASE := android-xiaomi

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildFingerprint=POCO/duchamp_global/duchamp:14/UP1A.230905.011/OS3.0.6.0.WNLMIXM:user/release-keys \
    DeviceProduct=$(PRODUCT_SYSTEM_NAME)

# LunarisAOSP
LUNARIS_BUILD_TYPE := OFFICIAL
TARGET_CUSTOM_UDFPS := true
WITH_GMS := true
WITH_GMS_COMMS_SUITE := true
WITH_PIXEL_LAUNCHER := false
TARGET_USE_MAPS := true
TARGET_USE_FILES := true
TARGET_USE_GPHOTOS := true
TARGET_USE_WALLPAPERS := true
PERF_ANIM_OVERRIDE := true
USE_REALITY_ENGINE := false
SURFACE_FLINGER_BOOST := true
