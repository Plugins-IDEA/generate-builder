# generate-builder 是什么？
> generate-builder 是IDEA的一款插件，覆盖了自动生成setter、getter、toString、Strucutre操作，一次即可生成，并且增强了实体的可操作性。

# 怎么使用？
以前的实体初始化：
```java
User user = new User();
user.setId(1);
user.setName("nzlong");
user.setAge(12);
```
增强之后你可以这样：
```java
User user = User.builder()
                .setId(1)
                .setName("nzlong")
                .setAge(12)
                .build();
```
还可以：
```java
User user = User.builder(user)
                .setId(user.getUserId() == null ? 1 : user.getUserId())
                .setName("nzlong")
                .setAge(12)
                .build();
```
