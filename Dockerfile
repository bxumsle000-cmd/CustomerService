# ---------- 第一階段：使用 Maven + JDK 21 編譯專案 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 先複製 pom.xml 並下載依賴，可利用 Docker layer cache 加速後續建置
COPY pom.xml .
RUN mvn -B dependency:go-offline

# 複製原始碼並打包（跳過測試以加快建置速度）
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------- 第二階段：使用輕量 JRE 21 執行 jar ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 從build階段複製打包好的 jar 檔
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
