package com.edu.unal.haspima.slmsondevice

import com.edu.unal.haspima.slmsondevice.data.ModelCatalog
import com.edu.unal.haspima.slmsondevice.data.formatBytes
import com.edu.unal.haspima.slmsondevice.data.hasLiteRtLmMagic
import com.edu.unal.haspima.slmsondevice.data.sanitizeModelFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    @Test
    fun catalogEntriesAreConsistent() {
        val ids = ModelCatalog.models.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ModelCatalog.models.forEach {
            assertTrue(it.fileName.endsWith(".litertlm"))
            assertTrue("URL debe terminar en el archivo", it.url.endsWith("/" + it.fileName))
            assertTrue(it.sizeBytes > 0)
        }
        assertFalse(ModelCatalog.default.gated)
    }

    @Test
    fun sanitizesImportedNames() {
        assertEquals("modelo.litertlm", sanitizeModelFileName("modelo.litertlm"))
        assertEquals("mi_modelo.litertlm", sanitizeModelFileName("mi modelo"))
        assertEquals("passwd.litertlm", sanitizeModelFileName("../../etc/passwd"))
        assertEquals("modelo_importado.litertlm", sanitizeModelFileName(null))
    }

    @Test
    fun formatsSizes() {
        assertEquals("614 MB", formatBytes(613_722_264))
        assertEquals("1.60 GB", formatBytes(1_597_922_856))
    }

    @Test
    fun detectsLiteRtLmMagic() {
        assertTrue(hasLiteRtLmMagic("LITERTLM\u0001\u0000".toByteArray()))
        assertFalse(hasLiteRtLmMagic("PK\u0003\u0004xxxx".toByteArray()))
    }
}
