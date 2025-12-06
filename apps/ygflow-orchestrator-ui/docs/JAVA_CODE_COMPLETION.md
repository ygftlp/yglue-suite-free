# Java 代码提示功能说明

## 功能概述

为脚本编辑器添加了完整的 Java/Groovy 代码智能提示功能，大幅提升开发体验。

## 新增功能

### 1. Java 语法高亮

- 使用 `@codemirror/lang-java` 提供 Java/Groovy 语法高亮
- 支持关键字、字符串、注释、数字等语法元素的高亮显示
- 自动识别 Java 代码结构

### 2. 智能代码补全

#### 2.1 Java 常用类补全

自动提示 30+ 常用 Java 类，包括：

**基础类型包装类**
- `String`, `Integer`, `Long`, `Double`, `Float`, `Boolean`
- `BigDecimal` - 高精度十进制数

**日期时间类**
- `Date`, `LocalDate`, `LocalDateTime`

**集合框架**
- `List`, `ArrayList`, `Map`, `HashMap`, `Set`, `HashSet`
- `Optional`, `Stream`, `Collections`, `Arrays`

**工具类**
- `Objects`, `System`, `Math`, `UUID`
- `Pattern`, `Matcher` - 正则表达式

#### 2.2 Java 常用方法补全

自动提示 50+ 常用方法，包括：

**String 方法**
- `length()`, `isEmpty()`, `substring()`, `toLowerCase()`, `toUpperCase()`
- `trim()`, `split()`, `replace()`, `contains()`, `startsWith()`, `endsWith()`
- `equals()`, `equalsIgnoreCase()`, `indexOf()`, `lastIndexOf()`, `matches()`

**List/Collection 方法**
- `size()`, `add()`, `get()`, `remove()`, `clear()`, `contains()`, `isEmpty()`
- `stream()`, `forEach()`, `filter()`, `map()`, `collect()`, `findFirst()`, `anyMatch()`

**Map 方法**
- `put()`, `get()`, `remove()`, `containsKey()`, `containsValue()`
- `keySet()`, `values()`, `entrySet()`, `getOrDefault()`, `putIfAbsent()`

**Object 方法**
- `toString()`, `hashCode()`, `equals()`, `getClass()`

#### 2.3 Groovy 关键字补全

支持 30+ Groovy 关键字提示：

**定义关键字**
- `def`, `class`, `interface`, `import`

**控制流**
- `if`, `else`, `for`, `while`, `return`

**异常处理**
- `try`, `catch`, `finally`, `throw`

**访问修饰符**
- `public`, `private`, `protected`, `static`, `final`

**特殊关键字**
- `new`, `this`, `super`, `null`, `true`, `false`
- `instanceof`, `as`, `in`, `void`

#### 2.4 变量和上下文补全

**上下文变量**
- `ctx`, `input`, `output`, `resolved`, `request`

**请求对象属性**
- `request.path`, `request.query`, `request.body`, `request.headers`
- 自动根据端点 Schema 提示路径变量、查询参数、请求体字段

**智能方法提示**
- 当输入 `.` 时，自动提示对象的方法和属性
- 支持链式调用的智能提示

#### 2.5 Import 语句智能补全

- 输入 `import` 后自动提示可用的类
- 支持 Java 标准库类和项目自定义类
- 模糊匹配，快速查找需要的类

## 使用示例

### 示例 1：字符串操作

```groovy
def str = "Hello World"
str.   // 自动提示: toLowerCase(), toUpperCase(), trim(), split(), ...
```

### 示例 2：集合操作

```groovy
def list = [1, 2, 3, 4, 5]
list.  // 自动提示: stream(), filter(), map(), forEach(), ...
```

### 示例 3：Map 操作

```groovy
def map = ["key": "value"]
map.   // 自动提示: get(), put(), containsKey(), keySet(), ...
```

### 示例 4：上下文访问

```groovy
ctx.   // 自动提示: _lastNodeResult, request, 以及其他上下文变量
input. // 自动提示: Java 对象的常用方法
```

### 示例 5：Import 语句

```groovy
import   // 自动提示: java.util.List, java.time.LocalDateTime, ...
```

## 技术实现

### 依赖

- `@codemirror/lang-java` - Java 语法支持
- `@codemirror/autocomplete` - 自动补全引擎

### 核心组件

1. **JAVA_COMMON_CLASSES** - Java 常用类定义
2. **JAVA_COMMON_METHODS** - Java 常用方法定义
3. **GROOVY_KEYWORDS** - Groovy 关键字定义
4. **createCompletionSource()** - 智能补全源构建函数
5. **getVariableCompletions()** - 变量属性补全函数

### 补全触发条件

- 输入字母时自动触发（`activateOnTyping: true`）
- 输入 `.` 时触发对象方法补全
- 输入 `[` 时触发属性访问补全
- 输入 `import` 时触发类引用补全

### 性能优化

- 最多显示 50 个补全选项（`maxRenderedOptions: 50`）
- 模糊匹配，不区分大小写
- 增量式补全，只在需要时加载

## 特性亮点

✅ **开箱即用** - 无需额外配置，打开编辑器即可使用  
✅ **智能提示** - 根据上下文提供相关的代码提示  
✅ **类型感知** - 区分类、方法、变量、关键字等不同类型  
✅ **快捷操作** - 支持 Tab 键选择、Enter 键确认  
✅ **详细文档** - 每个提示项都包含类型和描述信息  
✅ **中文友好** - 所有提示信息都有中文说明  

## 快捷键

- `Ctrl + Space` - 手动触发代码补全
- `Tab` / `Enter` - 确认选择
- `Esc` - 关闭补全菜单
- `↑` / `↓` - 导航补全列表
- `Ctrl + S` / `Cmd + S` - 保存并关闭编辑器

## 未来扩展

- [ ] 支持更多 Java 类库（如 Apache Commons、Guava 等）
- [ ] 根据类型推断提供更精确的方法提示
- [ ] 支持代码片段（Snippets）
- [ ] 支持方法参数提示
- [ ] 支持 JavaDoc 文档显示
- [ ] 支持类型检查和错误提示

## 注意事项

1. 代码补全基于静态规则，无法完全理解运行时类型
2. 对于自定义类，需要通过后端 API 获取类成员信息
3. 建议结合右侧的变量和函数面板使用，获得更完整的提示

---

**版本**: 1.0.0  
**更新日期**: 2025-12-02  
**作者**: Qoder AI Assistant
