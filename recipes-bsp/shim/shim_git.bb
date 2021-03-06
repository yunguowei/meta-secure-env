#
# Copyright (C) 2015-2017 Wind River Systems, Inc.
#

SUMMARY = "shim is a trivial EFI application."
DESCRIPTION = "shim is a trivial EFI application that, when run, attempts to open and \
execute another application. It will initially attempt to do this via the \
standard EFI LoadImage() and StartImage() calls. If these fail (because secure \
boot is enabled and the binary is not signed with an appropriate key, for \
instance) it will then validate the binary against a built-in certificate. If \
this succeeds and if the binary or signing key are not blacklisted then shim \
will relocate and execute the binary."
HOMEPAGE = "https://github.com/rhinstaller/shim.git"
SECTION = "bootloaders"

LICENSE = "shim"
LIC_FILES_CHKSUM = "file://COPYRIGHT;md5=b92e63892681ee4e8d27e7a7e87ef2bc"
PR = "r0"
SRC_URI = " \
        git://github.com/rhinstaller/shim.git \
        file://shim-allow-to-verify-sha1-digest-for-Authenticode.patch \
        file://Update-verification_method-if-the-loaded-image-is-si.patch  \
        file://Skip-the-error-message-when-creating-MokListRT-if-ve.patch \
        file://Allow-to-override-the-path-to-openssl.patch \
        file://Fix-for-the-cross-compilation.patch \
        file://Fix-signing-failure-due-to-not-finding-certificate.patch \
        file://Prevent-from-removing-intermediate-.efi.patch \
        file://Use-sbsign-to-sign-MokManager-and-fallback.patch \
        file://Fix-the-world-build-failure-due-to-the-missing-rule-.patch \
        file://Don-t-enforce-to-use-gnu89-standard.patch \
	file://Makefile-do-not-sign-the-efi-file.patch \
"
SRCREV = "9f2c83e60e0758c3db387eebaed3f306ad6214a8"
PV = "0.9+git${SRCPV}"

COMPATIBLE_HOST = '(i.86|x86_64).*-linux'

inherit deploy user-key-store

S = "${WORKDIR}/git"
DEPENDS_append = "\
    gnu-efi nss openssl util-linux-native openssl-native nss-native \
    sbsigntool-native \
"

EFI_ARCH_x86 = "ia32"
EFI_ARCH_x86-64 = "x64"

EXTRA_OEMAKE = " \
	CROSS_COMPILE="${TARGET_PREFIX}" \
	LIB_GCC="`${CC} -print-libgcc-file-name`" \
	LIB_PATH="${STAGING_LIBDIR}" \
	EFI_PATH="${STAGING_LIBDIR}" \
	EFI_INCLUDE="${STAGING_INCDIR}/efi" \
	RELEASE="_${DISTRO}_${DISTRO_VERSION}" \
	DEFAULT_LOADER=\\\\\\grub${EFI_ARCH}.efi \
	OPENSSL=${STAGING_BINDIR_NATIVE}/openssl \
	HEXDUMP=${STAGING_BINDIR_NATIVE}/hexdump \
	PK12UTIL=${STAGING_BINDIR_NATIVE}/pk12util \
	CERTUTIL=${STAGING_BINDIR_NATIVE}/certutil \
	SBSIGN=${STAGING_BINDIR_NATIVE}/sbsign \
	AR=${AR} \
"

PARALLEL_MAKE = ""

EFI_TARGET = "/boot/efi/EFI/BOOT"
FILES_${PN} += "${EFI_TARGET}"

# Prepare the signing certificate and keys
python do_prepare_signing_keys() {
    shim_prepare_sb_keys(d)
}
addtask prepare_signing_keys after do_check_user_keys before do_compile

python do_sign() {
    shim_sb_sign('${S}/shim${EFI_ARCH}.efi', '${B}/shim${EFI_ARCH}.efi.signed', d)
    sb_sign('${S}/mm${EFI_ARCH}.efi', '${B}/mm${EFI_ARCH}.efi.signed', d)
    sb_sign('${S}/fb${EFI_ARCH}.efi', '${B}/fb${EFI_ARCH}.efi.signed', d)
}
addtask sign after do_compile before do_install

do_install() {
    install -d ${D}${EFI_TARGET}

    local shim_dst="${D}${EFI_TARGET}/boot${EFI_ARCH}.efi"
    local mm_dst="${D}${EFI_TARGET}/mm${EFI_ARCH}.efi"
    if [ x"${UEFI_SB}" = x"1" ]; then
        install -m 0600 ${B}/shim${EFI_ARCH}.efi.signed $shim_dst
        install -m 0600 ${B}/mm${EFI_ARCH}.efi.signed $mm_dst
    else
        install -m 0600 ${B}/shim${EFI_ARCH}.efi $shim_dst
        install -m 0600 ${B}/mm${EFI_ARCH}.efi $mm_dst
    fi
}

# Install the unsigned images for manual signing
do_deploy() {
    install -d ${DEPLOYDIR}/efi-unsigned

    install -m 0600 ${B}/shim${EFI_ARCH}.efi ${DEPLOYDIR}/efi-unsigned/boot${EFI_ARCH}.efi
    install -m 0600 ${B}/mm${EFI_ARCH}.efi ${DEPLOYDIR}/efi-unsigned/mm${EFI_ARCH}.efi

    install -m 0600 "${D}${EFI_TARGET}/boot${EFI_ARCH}.efi" "${DEPLOYDIR}"
    install -m 0600 "${D}${EFI_TARGET}/mm${EFI_ARCH}.efi" "${DEPLOYDIR}"
}
addtask deploy after do_install before do_build
