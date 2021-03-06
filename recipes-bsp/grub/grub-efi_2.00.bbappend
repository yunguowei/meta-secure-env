#
# Copyright (C) 2016-2017 Wind River Systems, Inc.
#

FILESEXTRAPATHS_prepend := "${THISDIR}/grub-efi:"

EXTRA_SRC_URI = " \
    ${@'file://efi-secure-boot.inc' if d.getVar('UEFI_SB', True) == '1' else ''} \
"

SRC_URI += " \
    file://0001-pe32.h-add-header-structures-for-TE-and-DOS-executab.patch \
    file://0002-shim-add-needed-data-structures.patch \
    file://0003-efi-chainloader-implement-an-UEFI-Exit-service-for-s.patch \
    file://0004-efi-chainloader-port-shim-to-grub.patch \
    file://0005-efi-chainloader-use-shim-to-load-and-verify-an-image.patch \
    file://0006-efi-chainloader-boot-the-image-using-shim.patch \
    file://0007-efi-chainloader-take-care-of-unload-undershim.patch \
    file://chainloader-handle-the-unauthenticated-image-by-shim.patch \
    file://chainloader-Don-t-check-empty-section-in-file-like-..patch \
    file://chainloader-Actually-find-the-relocations-correctly-.patch \
    file://Fix-32-bit-build-failures.patch \
    file://Grub-get-and-set-efi-variables.patch \
    file://grub-efi.cfg \
    file://boot-menu.inc \
    file://serial-redirect-control-x-fix.patch \
    ${EXTRA_SRC_URI} \
"

GRUB_BUILDIN_append = " chain ${@'efivar' if d.getVar('UEFI_SB', True) == '1' else ''}"

# For efi_call_foo and efi_shim_exit
CFLAGS_append = " -fno-toplevel-reorder"

# Set a default root specifier.
inherit user-key-store

python __anonymous () {
    if d.getVar('MOK_SB', True) != "1":
        return

    # Override the default filename if mok-secure-boot enabled.
    # grub-efi must be renamed as grub${arch}.efi for working with shim.
    import re

    target = d.getVar('TARGET_ARCH', True)
    if target == "x86_64":
        grubimage = "grubx64.efi"
    elif re.match('i.86', target):
        grubimage = "grubia32.efi"
    else:
        raise bb.parse.SkipPackage("grub-efi is incompatible with target %s" % target)

    d.setVar("GRUB_IMAGE", grubimage)
}

do_compile_append_class-native() {
    make grub-editenv
}

do_install_append_class-native() {
    install -m 0755 grub-editenv "${D}${bindir}"
}

do_install_append_class-target() {
    local menu="${WORKDIR}/boot-menu.inc"

    # Enable the default IMA rules if IMA is enabled and storage-encryption is
    # disabled. This is because unseal operation will fail when any PCR is
    # extended due to updating the aggregate integrity value by the default
    # IMA rules.
    [ x"${IMA}" = x"1" -a x"${@bb.utils.contains('DISTRO_FEATURES', 'storage-encryption', '1', '0', d)}" != x"1" ] && {
        ! grep -q "ima_policy=tcb" "$menu" &&
            sed -i 's/^\s*chainloader\s\+.*bzImage.*/& ima_policy=tcb/g' "$menu"
    }

    [ x"${UEFI_SB}" = x"1" ] && {
        # The linux command doesn't support secure boot. So replace
        # it with the chainloader command.
        sed -i 's/^\s*linux\s\+/&chainloader /g' "$menu"

        # The initrd command becomes not working if the linux command
        # is not launched.
        sed -i '/^\s*initrd\s\+/d' "$menu"

        # Don't allow to load the detached initramfs if the bundled kernel used.
        [ x"${INITRAMFS_IMAGE_BUNDLE}" = x"1" ] &&
            sed -i 's/\(^\s*chainloader\s\+.*bzImage.*\)\s\+initrd=[^[:space:]]*\(.*\)/\1\2/g' "$menu"
    }

    # Install the stacked grub configs.
    install -d "${D}${EFI_BOOT_PATH}"
    install -m 0600 "${WORKDIR}/grub-efi.cfg" "${D}${EFI_BOOT_PATH}/grub.cfg"
    install -m 0600 "$menu" "${D}${EFI_BOOT_PATH}"
    [ x"${UEFI_SB}" = x"1" ] &&
        install -m 0600 "${WORKDIR}/efi-secure-boot.inc" "${D}${EFI_BOOT_PATH}"

    # Create the initial environment block with empty item.
    grub-editenv "${D}${EFI_BOOT_PATH}/grubenv" create
}

python do_sign_class-target() {
    _ = '${D}${EFI_BOOT_PATH}/${GRUB_IMAGE}'
    sb_sign(_, _, d)
}

python do_sign() {
}
addtask sign after do_install before do_deploy do_package

do_deploy_append_class-target() {
    install -d "${DEPLOYDIR}/efi-unsigned"

    install -m 0600 "${B}/${GRUB_IMAGE}" "${DEPLOYDIR}/efi-unsigned"
    cp -af "${D}${EFI_BOOT_PATH}/${GRUB_TARGET}-efi" "${DEPLOYDIR}/efi-unsigned"
}

CONFFILES_${PN} += " \
    ${EFI_BOOT_PATH}/grubenv \
    ${EFI_BOOT_PATH}/boot-menu.inc \
    ${EFI_BOOT_PATH}/efi-secure-boot.inc \
"
