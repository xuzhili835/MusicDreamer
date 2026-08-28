<template>
  <view class="page">
    <view class="card hero">
      <text class="hello">你好,{{ nickname }}</text>
      <text class="sub">小程序端骨架已打通登录闭环,后续页面在此工程内迭代</text>
    </view>

    <view class="card note">
      <view class="row">
        <text class="k">用户 ID</text>
        <text class="v">{{ user.userId }}</text>
      </view>
      <view class="row">
        <text class="k">角色</text>
        <text class="v">{{ roleText }}</text>
      </view>
      <view class="row">
        <text class="k">登录方式</text>
        <text class="v">已生效(JWT)</text>
      </view>
    </view>

    <button class="btn ghost" @click="logout">退出登录</button>
  </view>
</template>

<script>
import { getUser, clearAuth } from '../../utils/auth'
import { getUserInfo } from '../../api/user'

export default {
  data() {
    return { user: {} }
  },
  computed: {
    nickname() {
      return this.user.nickname || '悦友'
    },
    roleText() {
      const map = { 0: '普通用户', 1: '认证歌手', 2: '管理员' }
      return map[this.user.role] ?? '未知'
    },
  },
  onShow() {
    if (!uni.getStorageSync('md_token')) {
      return uni.reLaunch({ url: '/pages/login/login' })
    }
    this.user = getUser() || {}
    // 登录态探活:2006(改密吊销/过期)由 request 封装统一清 token 回登录页
    getUserInfo()
      .then((info) => {
        if (info && info.nickname) {
          this.user = { ...this.user, nickname: info.nickname, avatar: info.avatar, role: info.role }
        }
      })
      .catch(() => {})
  },
  methods: {
    logout() {
      clearAuth()
      uni.reLaunch({ url: '/pages/login/login' })
    },
  },
}
</script>

<style lang="scss" scoped>
.page {
  padding: 32rpx;
}

.card {
  background: $md-card;
  border-radius: 28rpx;
  padding: 40rpx 36rpx;
  margin-bottom: 28rpx;
  box-shadow: 0 6rpx 20rpx rgba(58, 46, 51, 0.05);

  &.hero {
    background: linear-gradient(135deg, #ffeef3, #ffffff);

    .hello {
      display: block;
      font-size: 40rpx;
      font-weight: 700;
      color: $md-text;
    }

    .sub {
      display: block;
      margin-top: 12rpx;
      font-size: 24rpx;
      color: $md-text-sub;
    }
  }

  .row {
    display: flex;
    justify-content: space-between;
    padding: 14rpx 0;
    font-size: 28rpx;

    .k {
      color: $md-text-sub;
    }

    .v {
      color: $md-text;
    }
  }
}

.btn.ghost {
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 46rpx;
  background: #ffffff;
  color: $md-primary-deep;
  border: 2rpx solid #ffd3e0;
}
</style>
