package io.github.sifisofakude.filesystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamTest	{
	val fs = FileSystems.current

	@Test
	fun inputStreamTest()	{
		val file = FileOperation("../pom.xml").apply	{
			if(!exists()) createNewFile()
		}

		assertTrue(file.exists())

		val inputStream = file.openInputStream()

		assertNotNull(inputStream)
	}

	@Test
	fun outputStreamTest()	{
		val file = FileOperation("../pom.xml")

		val ouputStream = file.openOutputStream()

		assertNotNull(ouputStream)
	}
}
