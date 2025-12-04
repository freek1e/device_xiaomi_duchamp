#
# Copyright (C) 2023 The LineageOS Project
#
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

PRODUCT_GMS_CLIENTID_BASE := android-xiaomi

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildFingerprint=POCO/duchamp_global/duchamp:14/UP1A.230905.011/OS2.0.205.0.VNLMIXM:user/release-keys \
    DeviceProduct=$(PRODUCT_SYSTEM_NAME)

# Lunch banner maintainer variable
RISING_MAINTAINER="@adarsh_8300u"

# Set RISING_MAINTAINER for version control 
PRODUCT_BUILD_PROP_OVERRIDES += \
    RisingChipset="Dimensity 8300-Ultra" \
    RisingMaintainer="@adarsh_8300u"

RISING_MAINTAINER := @adarsh_8300u

# Disable/enable blur support
TARGET_ENABLE_BLUR := true

# Whether to ship aperture camera
PRODUCT_NO_CAMERA := false

# Whether to ship lawnchair launcher
TARGET_PREBUILT_LAWNCHAIR_LAUNCHER := true
