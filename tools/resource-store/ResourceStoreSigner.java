import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/** 资源目录离线签名工具。私钥文件必须保存在仓库之外或已忽略的本机目录。 */
public final class ResourceStoreSigner {
    private static final String ALGORITHM = "SHA256withECDSA";

    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("generate")) {
            generate(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        if (args.length == 5 && args[0].equals("sign")) {
            sign(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), args[4]);
            return;
        }
        throw new IllegalArgumentException(
            "用法：generate <private-key> <public-key> | sign <private-key> <payload> <signature> <key-id>"
        );
    }

    private static void generate(Path privateKey, Path publicKey) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        writeAtomic(privateKey, Base64.getEncoder().encode(pair.getPrivate().getEncoded()));
        writeAtomic(publicKey, Base64.getEncoder().encode(pair.getPublic().getEncoded()));
    }

    private static void sign(Path privateKey, Path payload, Path output, String keyId) throws Exception {
        byte[] privateBytes = Base64.getDecoder().decode(Files.readString(privateKey).trim());
        var key = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
        Signature signer = Signature.getInstance(ALGORITHM);
        signer.initSign(key);
        signer.update(Files.readAllBytes(payload));
        String signature = Base64.getEncoder().encodeToString(signer.sign());
        String document = "{\n" +
            "  \"schemaVersion\": 1,\n" +
            "  \"keyId\": \"" + escape(keyId) + "\",\n" +
            "  \"algorithm\": \"" + ALGORITHM + "\",\n" +
            "  \"signature\": \"" + signature + "\"\n" +
            "}\n";
        writeAtomic(output, document.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeAtomic(Path target, byte[] bytes) throws Exception {
        Path parent = target.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path stage = Files.createTempFile(parent, ".pending-", ".tmp");
        try {
            Files.write(stage, bytes);
            try {
                Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(stage, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(stage);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
