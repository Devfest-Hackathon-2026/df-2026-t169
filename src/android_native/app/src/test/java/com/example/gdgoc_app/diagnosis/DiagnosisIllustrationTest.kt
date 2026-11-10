package com.example.gdgoc_app.diagnosis

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Test

class DiagnosisIllustrationTest {
    @Test fun allSixteenApiTypeCodesHaveDecodableUppercasePngAssets() {
        val expected=diagnosisSpec().types.map { "${it.typeCode}.png" }.toSet()
        assertEquals(16,expected.size)
        val folder=File("src/main/assets/diagnosis_illustrations")
        assertEquals(expected,folder.listFiles()!!.map { it.name }.toSet())
        expected.forEach { name ->
            assertTrue(name.matches(Regex("[GE][IX][PF][SD]\\.png")))
            val image=ImageIO.read(File(folder,name))
            assertNotNull("Cannot decode $name",image)
            assertTrue(image.width>0 && image.height>0)
            assertTrue("$name must support transparency",image.colorModel.hasAlpha())
            assertTrue("$name must contain transparent pixels",(0 until image.height).any { y ->
                (0 until image.width).any { x -> (image.getRGB(x,y) ushr 24)<255 }
            })
        }
    }
}
