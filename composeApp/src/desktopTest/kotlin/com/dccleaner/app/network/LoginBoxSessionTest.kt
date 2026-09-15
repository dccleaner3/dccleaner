package com.dccleaner.app.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginBoxSessionTest {
    @Test
    fun detectsLoggedInLoginBox() {
        assertTrue(loginBoxShowsLoggedIn("<div id='login_box'><a class='logout'>로그아웃</a></div>"))
        assertFalse(loginBoxShowsLoggedIn("<div id='login_box'><form id='login_process'></form></div>"))
    }
}
