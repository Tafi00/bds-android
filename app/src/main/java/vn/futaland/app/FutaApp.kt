package vn.futaland.app

import android.app.Application
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.designsystem.ToastCenter
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory

class FutaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val client = APIClient.init(this)
        client.onSessionExpired = {
            AppSession.shared.logout()
            ToastCenter.show("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.", isError = true)
        }

        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(OkHttpNetworkFetcherFactory())
                }
                .build()
        }
    }
}
