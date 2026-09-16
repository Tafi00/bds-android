package vn.futaland.app

import android.app.Application
import vn.futaland.app.core.auth.AppSession
import vn.futaland.app.core.network.APIClient
import vn.futaland.app.designsystem.ToastCenter
import vn.futaland.app.features.messaging.FutaMessagingService
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

        // Create the FCM notification channel early so background notifications
        // (which reference it by id) can be posted even before any activity runs.
        FutaMessagingService.createChannel(this)

        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(OkHttpNetworkFetcherFactory())
                }
                .build()
        }
    }
}
