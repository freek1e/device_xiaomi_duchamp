#!/bin/bash

echo "Applying some patches..."

DEVICE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
PATCH_DIR="$DEVICE_DIR/patches"
ROOT_DIR="$DEVICE_DIR/../../.."

apply_patch() {
    local target_dir="$ROOT_DIR/$1"
    local patch_file="$PATCH_DIR/$2"

    if [ ! -d "$target_dir" ]; then
        echo " Target directory $target_dir does not exist. Skipping."
        return
    fi

    echo " Checking $2 for $1..."
    
    pushd "$target_dir" > /dev/null || return

    if git apply --check --reverse --recount "$patch_file" > /dev/null 2>&1; then
        echo "    Patch is already applied. Skipping."
    else
        git apply --recount --whitespace=fix "$patch_file" > /dev/null 2>&1
        if [ $? -eq 0 ]; then
            echo "    Successfully applied."
        else
            echo "    Failed to apply! Reason:"
            git apply --check --recount "$patch_file"
        fi
    fi

    popd > /dev/null
}

apply_patch "hardware/xiaomi" "0001-TouchFeature.patch"
apply_patch "hardware/xiaomi" "0002-DisplayFeature.patch"
apply_patch "hardware/xiaomi" "0003-UDFPS.patch"
apply_patch "hardware/xiaomi" "0004-FP-Base.patch"


echo "Done"