package com.thanhnb.novelnative.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Generator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun generate() = rule.collect(PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        browse()
    }
}
