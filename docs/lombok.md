> 📌 本文件僅供個人參考閱讀，AI 寫 Code 時請勿參考此檔案內容。

# Lombok 常見用法整理

Lombok 是很成熟穩定的函式庫，以下整理新手最常用到的註解。

---

## 一、最常用(幾乎每個 entity/DTO 都會用)

### `@Getter` / `@Setter`
自動產生 getter/setter，可放在類別上(全部欄位)或單一欄位上。

```java
@Getter
@Setter
public class User {
    private String name;
    private int age;
}
// 自動有 getName()、setName()、getAge()、setAge()
```

### `@ToString`
自動產生 `toString()`。

```java
@ToString(exclude = "password") // 可排除某些欄位
public class User {
    private String name;
    private String password;
}
```

### `@EqualsAndHashCode`
自動產生 `equals()` 與 `hashCode()`。

```java
@EqualsAndHashCode
public class User {
    private String id;
    private String name;
}
```

---

## 二、建構子相關

- `@NoArgsConstructor` — 無參數建構子
- `@AllArgsConstructor` — 全參數建構子
- `@RequiredArgsConstructor` — 只針對 `final` 欄位或 `@NonNull` 欄位產生建構子

```java
@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
public class User {
    private final String id;   // 會進入 RequiredArgsConstructor
    private String name;
}
```

---

## 三、懶人包組合

### `@Data`
等於 `@Getter` + `@Setter` + `@ToString` + `@EqualsAndHashCode` + `@RequiredArgsConstructor` 一次搞定。DTO/entity 最常用。

```java
@Data
public class User {
    private String name;
    private int age;
}
```

### `@Value`
不可變(immutable)版本的 `@Data`。所有欄位變成 `private final`，只有 getter 沒有 setter。

```java
@Value
public class Point {
    int x;
    int y;
}
// 建立後就不能改，適合當作值物件
```

---

## 四、建立物件的好用工具

### `@Builder`
提供 builder 模式，欄位多的時候特別好用。

```java
@Builder
public class User {
    private String name;
    private int age;
}

// 使用方式
User user = User.builder()
        .name("Poz")
        .age(30)
        .build();
```

---

## 五、其他常見

### `@Slf4j`
自動產生一個叫 `log` 的 logger(需搭配 SLF4J),寫 log 很方便。

```java
@Slf4j
public class UserService {
    public void doSomething() {
        log.info("processing...");
    }
}
```

### `@NonNull`
放在參數上，呼叫時若傳 null 會自動丟出 `NullPointerException`。

```java
public void setName(@NonNull String name) {
    this.name = name;
}
```

---

## 給新手的實務建議

一開始只要記住 **`@Data`** + **`@Builder`** + **`@Slf4j`** 這三個，就能應付大部分情況了。等熟悉後再視需求加入其他註解。

> 小提醒:`@Data` 用在 JPA entity 上時，它產生的 `@EqualsAndHashCode` 有時會踩到雷(特別是有雙向關聯時)。這部分等真的遇到再處理即可，現階段先知道有這回事就好。