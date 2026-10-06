import com.android.apksig.ApkVerifier;
import com.android.apksig.SigningCertificateLineage;

import java.io.File;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.List;

public final class VerifyApkSigningLineage {
    private VerifyApkSigningLineage() {}

    public static void main(String[] args) throws Exception {
        require(args.length >= 3,
                "usage: VerifyApkSigningLineage APK CURRENT_SHA256 LINEAGE_SHA256...");

        File apk = new File(args[0]);
        ApkVerifier.Result result = new ApkVerifier.Builder(apk)
                .setMinCheckedPlatformVersion(29)
                .build()
                .verify();

        require(result.isVerified(), "APK signature verification failed: " + result.getAllErrors());
        require(!result.isVerifiedUsingV1Scheme(), "APK unexpectedly contains a v1 signature");
        require(!result.isVerifiedUsingV2Scheme(), "APK unexpectedly contains a v2 signature");
        require(result.isVerifiedUsingV3Scheme(), "APK does not contain a verified v3 signature");

        List<X509Certificate> currentSigners = result.getSignerCertificates();
        require(currentSigners.size() == 1, "APK does not have exactly one current signer");
        require(sha256(currentSigners.get(0)).equals(normalize(args[1])),
                "current signer fingerprint does not match");

        SigningCertificateLineage lineage = result.getSigningCertificateLineage();
        require(lineage != null, "APK does not contain a signing certificate lineage");
        List<X509Certificate> certificates = lineage.getCertificatesInLineage();
        require(certificates.size() == args.length - 2,
                "unexpected embedded lineage length: " + certificates.size());

        for (int index = 0; index < certificates.size(); index++) {
            X509Certificate certificate = certificates.get(index);
            require(sha256(certificate).equals(normalize(args[index + 2])),
                    "lineage fingerprint mismatch at position " + (index + 1));
            SigningCertificateLineage.SignerCapabilities capabilities =
                    lineage.getSignerCapabilities(certificate);
            require(capabilities.hasInstalledData(),
                    "installed-data capability is false at position " + (index + 1));
            require(!capabilities.hasSharedUid(),
                    "shared-uid capability is true at position " + (index + 1));
            require(capabilities.hasPermission(),
                    "permission capability is false at position " + (index + 1));
            require(!capabilities.hasRollback(),
                    "rollback capability is true at position " + (index + 1));
            require(!capabilities.hasAuth(),
                    "auth capability is true at position " + (index + 1));
        }

        System.out.println("current_signer_sha256=" + sha256(currentSigners.get(0)));
        for (int index = 0; index < certificates.size(); index++) {
            System.out.printf("lineage_%d_sha256=%s%n", index + 1, sha256(certificates.get(index)));
        }
        System.out.println("verified_scheme=v3");
    }

    private static String sha256(X509Certificate certificate) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        StringBuilder output = new StringBuilder(digest.length * 2);
        for (byte value : digest) output.append(String.format("%02x", value & 0xff));
        return output.toString();
    }

    private static String normalize(String fingerprint) {
        return fingerprint.replace(":", "").toLowerCase();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
