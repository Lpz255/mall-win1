package com.example.demo.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.demo.config.UserSessionProperties;
import com.example.demo.entity.UserAccount;
import com.example.demo.mapper.UserAccountMapper;
import com.example.demo.utils.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Demo 项目运行时上下文（数据库用户 + Redis 登录态 + 内存订单扩展态）。
 */
@Component
public class DemoContextService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoContextService.class);

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "123456";

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<String> DEFAULT_ADMIN_PERMISSIONS = List.of(
            "admin:dashboard:view",
            "admin:product:view",
            "admin:product:create",
            "admin:product:edit",
            "admin:product:delete",
            "admin:product:toggle",
            "admin:product:export",
            "admin:seckill:view",
            "admin:seckill:create",
            "admin:seckill:edit",
            "admin:seckill:start",
            "admin:seckill:stop",
            "admin:seckill:delete",
            "admin:seckill:export",
            "admin:order:view",
            "admin:order:detail",
            "admin:order:status",
            "admin:order:refund",
            "admin:order:export",
            "admin:user:view",
            "admin:user:toggle",
            "admin:user:export"
    );

    private final AtomicLong runtimeOrderItemIdGenerator = new AtomicLong(1);

    private final UserAccountMapper userAccountMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserSessionProperties userSessionProperties;

    private final ConcurrentMap<String, Long> userTokenIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> adminTokenIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> loginCodeStore = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ConcurrentMap<Long, CartEntry>> userCartStore = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, OrderExtra> orderExtraStore = new ConcurrentHashMap<>();

    public DemoContextService(UserAccountMapper userAccountMapper,
                              StringRedisTemplate stringRedisTemplate,
                              UserSessionProperties userSessionProperties) {
        this.userAccountMapper = userAccountMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.userSessionProperties = userSessionProperties;
    }

    @PostConstruct
    public void init() {
        ensureDefaultUser();
    }

    public Map<String, Object> loginByPassword(String phone, String password) {
        UserRecord user = findByPhone(phone);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (!Objects.equals(user.getPassword(), password)) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (!isEnabled(user.getStatus())) {
            throw new BusinessException(403, "当前账号已被禁用");
        }
        user = refreshUserLastLogin(user);
        return buildUserLoginResult(user);
    }

    public Map<String, Object> loginByCode(String phone, String code) {
        UserRecord user = findByPhone(phone);
        String expectedCode = loginCodeStore.getOrDefault(phone, "123456");
        if (!Objects.equals(expectedCode, code)) {
            throw new BusinessException(400, "验证码错误");
        }

        if (user == null) {
            user = registerUser(phone, "123456");
        }
        if (!isEnabled(user.getStatus())) {
            throw new BusinessException(403, "当前账号已被禁用");
        }
        user = refreshUserLastLogin(user);
        return buildUserLoginResult(user);
    }

    public Map<String, Object> register(String phone, String password) {
        if (findByPhone(phone) != null) {
            throw new BusinessException(409, "手机号已注册");
        }
        UserRecord user = registerUser(phone, password);
        user = refreshUserLastLogin(user);
        return buildUserLoginResult(user);
    }

    public void sendLoginCode(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() != 11) {
            throw new BusinessException(400, "手机号格式不正确");
        }
        loginCodeStore.put(phone, "123456");
    }

    public Map<String, Object> getUserProfile(String authorization) {
        UserRecord user = requireUser(authorization);
        return buildUserInfo(user);
    }

    public Long requireUserId(String authorization) {
        return requireUser(authorization).getId();
    }

    public UserRecord findUserById(Long userId) {
        if (userId == null) {
            return null;
        }
        UserAccount entity = userAccountMapper.selectById(userId);
        return toUserRecord(entity);
    }

    public Map<String, Object> adminLogin(String username, String password) {
        if (!Objects.equals(ADMIN_USERNAME, username) || !Objects.equals(ADMIN_PASSWORD, password)) {
            throw new BusinessException(401, "管理员账号或密码错误");
        }
        String token = "a_" + UUID.randomUUID().toString().replace("-", "");
        adminTokenIndex.put(token, username);

        Map<String, Object> adminInfo = new LinkedHashMap<>();
        adminInfo.put("id", 1);
        adminInfo.put("username", ADMIN_USERNAME);
        adminInfo.put("name", "系统管理员");
        adminInfo.put("role", "SUPER_ADMIN");
        adminInfo.put("permissions", DEFAULT_ADMIN_PERMISSIONS);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("adminInfo", adminInfo);
        result.put("permissions", DEFAULT_ADMIN_PERMISSIONS);
        return result;
    }

    public Map<String, Object> getAdminProfile(String authorization) {
        requireAdmin(authorization);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", 1);
        profile.put("username", ADMIN_USERNAME);
        profile.put("name", "系统管理员");
        profile.put("role", "SUPER_ADMIN");
        return profile;
    }

    public List<String> getAdminPermissions(String authorization) {
        requireAdmin(authorization);
        return DEFAULT_ADMIN_PERMISSIONS;
    }

    public List<Map<String, Object>> listUsers(String keyword, String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<UserAccount> queryWrapper = buildUserQueryWrapper(keyword, status, true);
        List<UserAccount> all = userAccountMapper.selectList(queryWrapper);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (UserAccount entity : all) {
            rows.add(buildAdminUserRow(toUserRecord(entity)));
        }
        return slice(rows, pageNum, pageSize);
    }

    public long countUsers(String keyword, String status) {
        LambdaQueryWrapper<UserAccount> queryWrapper = buildUserQueryWrapper(keyword, status, false);
        return userAccountMapper.selectCount(queryWrapper);
    }

    public void toggleUserStatus(Long userId, String status) {
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        String nextStatus = normalizeUserStatus(status);
        userAccountMapper.update(null, new LambdaUpdateWrapper<UserAccount>()
                .eq(UserAccount::getId, userId)
                .set(UserAccount::getStatus, nextStatus));
        if ("disabled".equalsIgnoreCase(nextStatus)) {
            clearAllUserSessions(userId);
        }
    }

    public List<CartEntry> listCart(Long userId) {
        ConcurrentMap<Long, CartEntry> cart = userCartStore.getOrDefault(userId, new ConcurrentHashMap<>());
        List<CartEntry> entries = new ArrayList<>(cart.values());
        entries.sort(Comparator.comparing(CartEntry::getUpdateTime).reversed());
        return entries;
    }

    public CartEntry addCartItem(Long userId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(400, "数量必须大于0");
        }
        ConcurrentMap<Long, CartEntry> cart = userCartStore.computeIfAbsent(userId, key -> new ConcurrentHashMap<>());
        CartEntry entry = cart.get(productId);
        if (entry == null) {
            entry = new CartEntry();
            entry.setId(runtimeOrderItemIdGenerator.incrementAndGet());
            entry.setProductId(productId);
            entry.setQuantity(quantity);
            entry.setChecked(true);
            entry.setUpdateTime(LocalDateTime.now());
            cart.put(productId, entry);
        } else {
            entry.setQuantity(entry.getQuantity() + quantity);
            entry.setUpdateTime(LocalDateTime.now());
        }
        return entry;
    }

    public void updateCartItem(Long userId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(400, "数量必须大于0");
        }
        ConcurrentMap<Long, CartEntry> cart = userCartStore.computeIfAbsent(userId, key -> new ConcurrentHashMap<>());
        CartEntry entry = cart.get(productId);
        if (entry == null) {
            throw new BusinessException(404, "购物车商品不存在");
        }
        entry.setQuantity(quantity);
        entry.setUpdateTime(LocalDateTime.now());
    }

    public void setCartChecked(Long userId, Long productId, boolean checked) {
        ConcurrentMap<Long, CartEntry> cart = userCartStore.computeIfAbsent(userId, key -> new ConcurrentHashMap<>());
        CartEntry entry = cart.get(productId);
        if (entry == null) {
            throw new BusinessException(404, "购物车商品不存在");
        }
        entry.setChecked(checked);
        entry.setUpdateTime(LocalDateTime.now());
    }

    public void removeCartItem(Long userId, Long productId) {
        ConcurrentMap<Long, CartEntry> cart = userCartStore.computeIfAbsent(userId, key -> new ConcurrentHashMap<>());
        cart.remove(productId);
    }

    public void clearCart(Long userId) {
        userCartStore.remove(userId);
    }

    public List<CartEntry> getCartEntriesByIds(Long userId, List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyList();
        }
        ConcurrentMap<Long, CartEntry> cart = userCartStore.computeIfAbsent(userId, key -> new ConcurrentHashMap<>());
        List<CartEntry> result = new ArrayList<>();
        for (Long productId : productIds) {
            CartEntry entry = cart.get(productId);
            if (entry != null) {
                result.add(entry);
            }
        }
        return result;
    }

    public void saveOrderExtra(Long orderId, OrderExtra extra) {
        orderExtraStore.put(orderId, extra);
    }

    public OrderExtra getOrderExtra(Long orderId) {
        return orderExtraStore.get(orderId);
    }

    public void updateOrderStatus(Long orderId, String status) {
        OrderExtra extra = orderExtraStore.get(orderId);
        if (extra == null) {
            extra = new OrderExtra();
            extra.setStatus(status);
            orderExtraStore.put(orderId, extra);
            return;
        }
        extra.setStatus(status);
    }

    public void updateRefundStatus(Long orderId, String refundStatus) {
        OrderExtra extra = orderExtraStore.get(orderId);
        if (extra == null) {
            extra = new OrderExtra();
            extra.setRefundStatus(refundStatus);
            orderExtraStore.put(orderId, extra);
            return;
        }
        extra.setRefundStatus(refundStatus);
    }

    public static String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return DATETIME_FORMATTER.format(value);
    }

    public static LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, DATETIME_FORMATTER);
        } catch (DateTimeParseException ignore) {
            try {
                return LocalDateTime.parse(text);
            } catch (DateTimeParseException ex) {
                throw new BusinessException(400, "时间格式不正确");
            }
        }
    }

    public static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    public static BigDecimal toBigDecimal(Object value, BigDecimal defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return defaultValue;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private UserRecord registerUser(String phone, String password) {
        LocalDateTime now = LocalDateTime.now();
        UserAccount entity = new UserAccount();
        entity.setPhone(phone);
        entity.setPassword(password);
        entity.setName("用户" + phone.substring(Math.max(0, phone.length() - 4)));
        entity.setUserLevel("普通会员");
        entity.setStatus("enabled");
        entity.setRegisterTime(now);
        entity.setLastLoginTime(now);
        int rows = userAccountMapper.insert(entity);
        if (rows <= 0 || entity.getId() == null) {
            throw new BusinessException(500, "用户注册失败，请稍后重试");
        }
        return toUserRecord(entity);
    }

    private Map<String, Object> buildUserLoginResult(UserRecord user) {
        String token = "u_" + UUID.randomUUID().toString().replace("-", "");
        saveUserSession(token, user.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("expiresIn", resolveSessionTtlSeconds());
        result.put("userInfo", buildUserInfo(user));
        return result;
    }

    private Map<String, Object> buildUserInfo(UserRecord user) {
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("name", user.getName());
        userInfo.put("phone", user.getPhone());
        userInfo.put("level", user.getLevel());
        userInfo.put("status", user.getStatus());
        userInfo.put("registerTime", formatDateTime(user.getRegisterTime()));
        userInfo.put("lastLoginTime", formatDateTime(user.getLastLoginTime()));
        return userInfo;
    }

    private UserRecord requireUser(String authorization) {
        String token = resolveBearerToken(authorization);
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(401, "请先登录");
        }
        Long userId = resolveUserIdByToken(token);
        if (userId == null) {
            throw new BusinessException(401, "登录状态已失效，请重新登录");
        }
        UserRecord user = findUserById(userId);
        if (user == null) {
            clearUserSession(token, userId);
            throw new BusinessException(401, "用户不存在");
        }
        if (!isEnabled(user.getStatus())) {
            clearUserSession(token, userId);
            throw new BusinessException(403, "当前账号已被禁用");
        }
        refreshUserSession(token, userId);
        return user;
    }

    private void requireAdmin(String authorization) {
        String token = resolveBearerToken(authorization);
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(401, "请先登录管理员账号");
        }
        String username = adminTokenIndex.get(token);
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(401, "管理员登录状态已失效，请重新登录");
        }
    }

    private UserRecord findByPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        UserAccount entity = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getPhone, phone)
                .last("limit 1"));
        return toUserRecord(entity);
    }

    private void ensureDefaultUser() {
        UserAccount exists = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getPhone, "13800138000")
                .last("limit 1"));
        if (exists != null) {
            return;
        }
        UserAccount defaultUser = new UserAccount();
        defaultUser.setPhone("13800138000");
        defaultUser.setPassword("123456");
        defaultUser.setName("演示用户8000");
        defaultUser.setUserLevel("普通会员");
        defaultUser.setStatus("enabled");
        defaultUser.setRegisterTime(LocalDateTime.now().minusDays(20));
        defaultUser.setLastLoginTime(LocalDateTime.now().minusHours(3));
        userAccountMapper.insert(defaultUser);
    }

    private UserRecord refreshUserLastLogin(UserRecord user) {
        if (user == null || user.getId() == null) {
            return user;
        }
        LocalDateTime now = LocalDateTime.now();
        userAccountMapper.update(null, new LambdaUpdateWrapper<UserAccount>()
                .eq(UserAccount::getId, user.getId())
                .set(UserAccount::getLastLoginTime, now));
        user.setLastLoginTime(now);
        return user;
    }

    private LambdaQueryWrapper<UserAccount> buildUserQueryWrapper(String keyword, String status, boolean withOrderBy) {
        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String text = keyword.trim();
            wrapper.and(w -> w.like(UserAccount::getName, text).or().like(UserAccount::getPhone, text));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(UserAccount::getStatus, normalizeUserStatus(status));
        }
        if (withOrderBy) {
            wrapper.orderByDesc(UserAccount::getId);
        }
        return wrapper;
    }

    private void clearAllUserSessions(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            String activeToken = stringRedisTemplate.opsForValue().get(buildUserIndexKey(userId));
            if (StringUtils.hasText(activeToken)) {
                clearUserSession(activeToken, userId);
            } else {
                stringRedisTemplate.delete(buildUserIndexKey(userId));
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("清理用户登录态失败。userId={}", userId, exception);
        }
        userTokenIndex.entrySet().removeIf(entry -> Objects.equals(entry.getValue(), userId));
    }

    private UserRecord toUserRecord(UserAccount entity) {
        if (entity == null) {
            return null;
        }
        UserRecord record = new UserRecord();
        record.setId(entity.getId());
        record.setPhone(entity.getPhone());
        record.setPassword(entity.getPassword());
        record.setName(entity.getName());
        record.setLevel(entity.getUserLevel());
        record.setStatus(normalizeUserStatus(entity.getStatus()));
        record.setRegisterTime(entity.getRegisterTime());
        record.setLastLoginTime(entity.getLastLoginTime());
        return record;
    }

    private String resolveBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        String text = authorization.trim();
        String prefix = "Bearer ";
        if (text.startsWith(prefix)) {
            return text.substring(prefix.length()).trim();
        }
        return text;
    }

    private void saveUserSession(String token, Long userId) {
        if (!StringUtils.hasText(token) || userId == null) {
            return;
        }
        userTokenIndex.put(token, userId);

        long ttlSeconds = resolveSessionTtlSeconds();
        String tokenKey = buildUserTokenKey(token);
        String userIndexKey = buildUserIndexKey(userId);
        try {
            String oldToken = stringRedisTemplate.opsForValue().get(userIndexKey);
            if (StringUtils.hasText(oldToken) && !Objects.equals(oldToken, token)) {
                stringRedisTemplate.delete(buildUserTokenKey(oldToken));
                userTokenIndex.remove(oldToken);
            }
            stringRedisTemplate.opsForValue().set(tokenKey, String.valueOf(userId), ttlSeconds, TimeUnit.SECONDS);
            stringRedisTemplate.opsForValue().set(userIndexKey, token, ttlSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException exception) {
            LOGGER.warn("写入 Redis 登录态失败，已降级到内存态。token={}", token, exception);
        }
    }

    private Long resolveUserIdByToken(String token) {
        Long fallback = userTokenIndex.get(token);
        try {
            String userIdText = stringRedisTemplate.opsForValue().get(buildUserTokenKey(token));
            if (!StringUtils.hasText(userIdText)) {
                return fallback;
            }
            Long userId = toLong(userIdText);
            if (userId == null) {
                stringRedisTemplate.delete(buildUserTokenKey(token));
                return fallback;
            }
            userTokenIndex.put(token, userId);
            return userId;
        } catch (RuntimeException exception) {
            LOGGER.warn("读取 Redis 登录态失败，使用内存态回退。token={}", token, exception);
            return fallback;
        }
    }

    private void refreshUserSession(String token, Long userId) {
        if (!StringUtils.hasText(token) || userId == null) {
            return;
        }
        long ttlSeconds = resolveSessionTtlSeconds();
        try {
            stringRedisTemplate.expire(buildUserTokenKey(token), ttlSeconds, TimeUnit.SECONDS);
            stringRedisTemplate.opsForValue().set(buildUserIndexKey(userId), token, ttlSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException exception) {
            LOGGER.warn("续期 Redis 登录态失败。token={}", token, exception);
        }
    }

    private void clearUserSession(String token, Long userId) {
        if (StringUtils.hasText(token)) {
            userTokenIndex.remove(token);
        }
        try {
            if (StringUtils.hasText(token)) {
                stringRedisTemplate.delete(buildUserTokenKey(token));
            }
            if (userId != null) {
                stringRedisTemplate.delete(buildUserIndexKey(userId));
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("清理 Redis 登录态失败。token={}, userId={}", token, userId, exception);
        }
    }

    private long resolveSessionTtlSeconds() {
        return Math.max(userSessionProperties.getTtlSeconds(), 60L);
    }

    private String buildUserTokenKey(String token) {
        return userSessionProperties.getTokenKeyPrefix() + token;
    }

    private String buildUserIndexKey(Long userId) {
        return userSessionProperties.getUserIndexKeyPrefix() + userId;
    }

    private static boolean isEnabled(String status) {
        return "enabled".equalsIgnoreCase(normalizeUserStatus(status));
    }

    private static String normalizeUserStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "enabled";
        }
        return "disabled".equalsIgnoreCase(status) ? "disabled" : "enabled";
    }

    private static <T> List<T> slice(List<T> source, int pageNum, int pageSize) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.max(pageSize, 1);
        int start = (safePageNum - 1) * safePageSize;
        if (start >= source.size()) {
            return Collections.emptyList();
        }
        int end = Math.min(source.size(), start + safePageSize);
        return source.subList(start, end);
    }

    private static Map<String, Object> buildAdminUserRow(UserRecord user) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("name", user.getName());
        row.put("phone", user.getPhone());
        row.put("level", user.getLevel());
        row.put("status", user.getStatus());
        row.put("registerTime", formatDateTime(user.getRegisterTime()));
        row.put("lastLoginTime", formatDateTime(user.getLastLoginTime()));
        return row;
    }

    public static class UserRecord {
        private Long id;
        private String phone;
        private String password;
        private String name;
        private String level;
        private String status;
        private LocalDateTime registerTime;
        private LocalDateTime lastLoginTime;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public LocalDateTime getRegisterTime() {
            return registerTime;
        }

        public void setRegisterTime(LocalDateTime registerTime) {
            this.registerTime = registerTime;
        }

        public LocalDateTime getLastLoginTime() {
            return lastLoginTime;
        }

        public void setLastLoginTime(LocalDateTime lastLoginTime) {
            this.lastLoginTime = lastLoginTime;
        }
    }

    public static class CartEntry {
        private Long id;
        private Long productId;
        private Integer quantity;
        private Boolean checked;
        private LocalDateTime updateTime;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public Boolean getChecked() {
            return checked;
        }

        public void setChecked(Boolean checked) {
            this.checked = checked;
        }

        public LocalDateTime getUpdateTime() {
            return updateTime;
        }

        public void setUpdateTime(LocalDateTime updateTime) {
            this.updateTime = updateTime;
        }
    }

    public static class OrderExtra {
        private String status;
        private String refundStatus;
        private String address;
        private String remark;
        private List<Map<String, Object>> items = new ArrayList<>();

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getRefundStatus() {
            return refundStatus;
        }

        public void setRefundStatus(String refundStatus) {
            this.refundStatus = refundStatus;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }

        public List<Map<String, Object>> getItems() {
            return items;
        }

        public void setItems(List<Map<String, Object>> items) {
            this.items = items;
        }
    }
}
