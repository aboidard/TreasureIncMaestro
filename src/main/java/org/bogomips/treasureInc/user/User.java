package org.bogomips.treasureInc.user;

import java.sql.Timestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User extends PanacheEntityBase {
    public static String  CHARACTERS_PRIVATE_KEY = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    public static String  CHARACTERS_PUBLIC_KEY = "0123456789";
    public static int DEFAULT_MONEY = 100;
    public static int PRIVATE_KEY_LENGTH = 30;
    public static int PUBLIC_KEY_LENGTH = 30;
    @Id
    @SequenceGenerator(name = "users_id_seq", sequenceName = "users_id_seq", allocationSize = 1)
    @GeneratedValue(generator = "users_id_seq")
    public Integer id;

    @Column(name = "public_key")
    public String publicKey;

    @JsonIgnore
    @Column(name = "private_key")
    public String privateKey;

    public Integer money;

    public Timestamp createdAt;

    public Timestamp updatedAt;
    public Timestamp lastLogin;

    public User() {
    }

    @SuppressWarnings("unused")
    public User(String publicKey, String privateKey, Integer money) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.money = money;
    }

    public static User findByPublicKey(String publicKey) {
        return find("publicKey", publicKey).firstResult();
    }

    public static long countByLastLoginGreaterThan(Timestamp lastLogin) {
        return find("lastLogin > ?1", lastLogin).count();
    }

    public static long countByCreatedAtGreaterThan(Timestamp timestamp) {
        return find("createdAt > ?1", timestamp).count();
    }

    public static String generatePrivateKey() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PRIVATE_KEY_LENGTH; i++) {
            int index = (int) (CHARACTERS_PRIVATE_KEY.length() * Math.random());
            sb.append(CHARACTERS_PRIVATE_KEY.charAt(index));
        }
        return sb.toString();
    }

    public static String generatePublicKey() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PUBLIC_KEY_LENGTH; i++) {
            int index = (int) (CHARACTERS_PUBLIC_KEY.length() * Math.random());
            sb.append(CHARACTERS_PUBLIC_KEY.charAt(index));
        }
        return sb.toString();
    }
}
