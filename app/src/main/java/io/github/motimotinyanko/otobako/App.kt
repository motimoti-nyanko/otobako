package io.github.motimotinyanko.otobako

import android.app.Application
import android.content.Intent
import io.github.motimotinyanko.otobako.data.Repo
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class App : Application() {
    lateinit var repo: Repo
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repo = Repo(this)
        // ダウンロード・書き出し中はアプリを閉じても止まらないよう前面サービスを立てる
        repo.scope.launch {
            repo.busy.distinctUntilChanged().collect { busy ->
                if (busy) runCatching { startForegroundService(Intent(this@App, WorkService::class.java)) }
            }
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
