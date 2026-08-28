<template>
  <view class="page">
    <view class="brand">
      <view class="brand-badge">♪</view>
      <text class="brand-title">悦享音乐</text>
      <text class="brand-sub">Music Dreamer · Sakura Echo</text>
    </view>

    <view class="form">
      <input class="ipt" v-model="username" placeholder="用户名" placeholder-class="ipt-ph" />
      <input class="ipt" v-model="password" password placeholder="密码" placeholder-class="ipt-ph" />
      <button class="btn primary" :loading="loading" :disabled="loading" @click="doLogin">登 录</button>

      <view class="divider">
        <view class="divider-line"></view>
        <text class="divider-text">或</text>
        <view class="divider-line"></view>
      </view>

      <button class="btn wx" :loading="wxLoading" :disabled="wxLoading" @click="doWxLogin">微信一键登录</button>
      <text class="tip">微信首次登录自动创建账号;新账号注册请使用网页端</text>
    </view>
  </view>
</template>

<script>
import { loginByUsername, loginByWxCode } from '../../api/user'
import { setToken, setUser } from '../../utils/auth'

export default {
  data() {
    return {
      username: '',
      password: '',
      loading: false,
      wxLoading: false,
    }
  },
  methods: {
    saveSession(data) {
      setToken(data.token)
      setUser({
        userId: data.userId,
        nickname: data.nickname,
        avatar: data.avatar,
        role: data.role,
      })
      uni.reLaunch({ url: '/pages/home/home' })
    },
    doLogin() {
      if (!this.username || !this.password) {
        return uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
      }
      this.loading = true
      loginByUsername(this.username, this.password)
        .then((data) => this.saveSession(data))
        .catch(() => {})
        .then(() => {
          this.loading = false
        })
    },
    doWxLogin() {
      this.wxLoading = true
      uni.login({
        provider: 'weixin',
        success: ({ code }) => {
          loginByWxCode(code)
            .then((data) => this.saveSession(data))
            .catch(() => {})
            .then(() => {
              this.wxLoading = false
            })
        },
        fail: () => {
          this.wxLoading = false
          uni.showToast({ title: '拉起微信登录失败', icon: 'none' })
        },
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #ffeef3 0%, $md-bg 45%);
  padding: 0 60rpx;
  box-sizing: border-box;
}

.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 180rpx;

  .brand-badge {
    width: 128rpx;
    height: 128rpx;
    border-radius: 40rpx;
    background: linear-gradient(135deg, $md-primary, $md-primary-deep);
    color: #fff;
    font-size: 64rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 12rpx 32rpx rgba(240, 98, 146, 0.35);
  }

  .brand-title {
    margin-top: 32rpx;
    font-size: 48rpx;
    font-weight: 700;
    color: $md-text;
  }

  .brand-sub {
    margin-top: 10rpx;
    font-size: 24rpx;
    color: $md-text-sub;
  }
}

.form {
  margin-top: 90rpx;

  .ipt {
    height: 96rpx;
    background: $md-card;
    border-radius: 24rpx;
    padding: 0 32rpx;
    margin-bottom: 28rpx;
    font-size: 30rpx;
    box-shadow: 0 4rpx 16rpx rgba(58, 46, 51, 0.05);
  }

  .btn {
    margin-top: 16rpx;
    height: 96rpx;
    line-height: 96rpx;
    border-radius: 48rpx;
    font-size: 32rpx;
    border: none;

    &.primary {
      background: linear-gradient(135deg, $md-primary, $md-primary-deep);
      color: #fff;
      box-shadow: 0 10rpx 28rpx rgba(240, 98, 146, 0.35);
    }

    &.wx {
      background: #07c160;
      color: #fff;
      box-shadow: 0 10rpx 28rpx rgba(7, 193, 96, 0.3);
    }
  }

  .divider {
    display: flex;
    align-items: center;
    margin: 44rpx 0 8rpx;

    .divider-line {
      flex: 1;
      height: 2rpx;
      background: #ece4e8;
    }

    .divider-text {
      padding: 0 24rpx;
      font-size: 24rpx;
      color: $md-text-sub;
    }
  }

  .tip {
    display: block;
    text-align: center;
    margin-top: 32rpx;
    font-size: 22rpx;
    color: $md-text-sub;
  }
}

.ipt-ph {
  color: #c4b8be;
}
</style>
