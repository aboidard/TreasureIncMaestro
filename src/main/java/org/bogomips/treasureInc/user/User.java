package org.bogomips.treasureInc.user;

import java.sql.Timestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends PanacheEntityBase {
    public static String  CHARACTERS_PRIVATE_KEY = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    public static int DEFAULT_MONEY = 100;
    public static int PRIVATE_KEY_LENGTH = 30;
    @Id
    @SequenceGenerator(name = "users_id_seq", sequenceName = "users_id_seq", allocationSize = 1)
    @GeneratedValue(generator = "users_id_seq")
    private Integer id;

    @Column(name = "public_key")
    private String publicKey;

    @JsonIgnore
    @Column(name = "private_key")
    private String privateKey;

    private Integer money;

    private Timestamp createdAt;

    private Timestamp updatedAt;
    private Timestamp lastLogin;

    public User() {
    }

    public User(String publicKey, String privateKey, Integer money) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.money = money;
    }

    public static User findByPublicKey(String publicKey) {
        return find("publicKey", publicKey).firstResult();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public Integer getMoney() {
        return money;
    }

    public void setMoney(Integer money) {
        this.money = money;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Timestamp getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(Timestamp lastLogin) {
        this.lastLogin = lastLogin;
    }

    public static String generatePrivateKey() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PRIVATE_KEY_LENGTH; i++) {
            int index = (int) (CHARACTERS_PRIVATE_KEY.length() * Math.random());
            sb.append(CHARACTERS_PRIVATE_KEY.charAt(index));
        }
        return sb.toString();
    }
}
