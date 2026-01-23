#!/system/bin/sh

# 全局变量 - 会由脚本引擎为其赋值
# 框架并不需要这些变量，如果你不需要可以将其删除
# 如有需要，你也可以增加一些自己的变量定义
# 但这个文件每次运行脚本都会被执行，不建议写太复杂的过程
export EXECUTOR_PATH="$({EXECUTOR_PATH})"
export START_DIR="$({START_DIR})"
export TEMP_DIR="$({TEMP_DIR})"
export ANDROID_UID="$({ANDROID_UID})"
export ANDROID_SDK="$({ANDROID_SDK})"
export SDCARD_PATH="$({SDCARD_PATH})"
export PACKAGE_NAME="$({PACKAGE_NAME})"
export PACKAGE_VERSION_NAME="$({PACKAGE_VERSION_NAME})"
export PACKAGE_VERSION_CODE="$({PACKAGE_VERSION_CODE})"
export APP_USER_ID="$({APP_USER_ID})"
export PATH_APK_APP="$({PATH_APK_APP})"
export DARK_MODE="$({DARK_MODE})"
export KERNEL_VERSION="$({KERNEL_VERSION})"
export TOTAL_MEMORY="$({TOTAL_MEMORY})"
export CPU_ABI="$({CPU_ABI})"
export LANGUAGE="$({LANGUAGE})"
export COUNTRY="$({COUNTRY})"
export TIMEZONE="$({TIMEZONE})"
export ANDROID_DEVICE="$({ANDROID_DEVICE})"
export ANDROID_BRAND="$({ANDROID_BRAND})"
export ANDROID_MANUFACTURER="$({ANDROID_MANUFACTURER})"
export ANDROID_FINGERPRINT="$({ANDROID_FINGERPRINT})"
export ANDROID_RELEASE="$({ANDROID_RELEASE})"
export ANDROID_MODEL="$({ANDROID_MODEL})"
export ANDROID_ID="$({ANDROID_ID})"
export START_TIME="$(date +%s)"

# ROOT_PERMISSION 取值为：true 或 false
export ROOT_PERMISSION=$({ROOT_PERMISSION})

# 修复非ROOT权限执行脚本时，无法写入默认的缓存目录 /data/local/tmp
export TMPDIR="$TEMP_DIR"

# toolkit工具目录
export TOOLKIT="$({TOOLKIT})"

# 添加toolkit添加为应用程序目录
if [ -d "$TOOLKIT" ]; then
    export PATH="$TOOLKIT:$PATH"
fi

# 判断是否有指定执行目录，跳转到起始目录
if [ -d "$START_DIR" ]
then
    cd "$START_DIR"
fi

# 运行脚本
if [ -f "$1" ]; then
    source "$1"
else
    echo "$1 已丢失" 1>&2
fi
