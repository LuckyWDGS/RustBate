package com.github.catvod.crawler

import android.content.Context

open class Spider {
    open fun init(context: Context?, extend: String?) = Unit

    open fun homeContent(filter: Boolean): String = ""

    open fun detailContent(ids: List<String>): String = ""

    open fun searchContent(key: String, quick: Boolean): String = ""

    open fun playerContent(flag: String, id: String, vipFlags: List<String>): String = ""
}

