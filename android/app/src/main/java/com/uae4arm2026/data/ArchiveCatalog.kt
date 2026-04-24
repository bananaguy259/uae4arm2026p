package com.uae4arm2026.data

import android.net.Uri
import com.uae4arm2026.data.model.FileCategory

data class ArchiveCollection(
	val id: String,
	val fallbackTitle: String,
	val identifier: String,
	val detailUrl: String,
	val category: FileCategory,
	val letter: Char? = null
)

data class ArchiveDownloadItem(
	val collectionId: String,
	val fileName: String,
	val sizeBytes: Long?,
	val downloadUrl: String,
	val category: FileCategory
) {
	val id: String
		get() = "$collectionId|$fileName"
}

data class ArchiveCollectionListing(
	val collection: ArchiveCollection,
	val archiveTitle: String,
	val files: List<ArchiveDownloadItem>
)

data class ArchiveDownloadProgress(
	val itemId: String,
	val fileName: String,
	val category: FileCategory,
	val downloadedBytes: Long,
	val totalBytes: Long? = null
) {
	val fractionComplete: Float?
		get() = totalBytes
			?.takeIf { it > 0L }
			?.let { downloadedBytes.toFloat() / it.toFloat() }
}

object ArchiveCatalog {
	private const val DETAILS_BASE_URL = "https://archive.org/details/"
	private const val DOWNLOAD_BASE_URL = "https://archive.org/download/"

	val kickstartCollection = ArchiveCollection(
		id = "kickstarts",
		fallbackTitle = "Commodore Amiga Firmware",
		identifier = "commodore-amiga-firmware",
		detailUrl = "${DETAILS_BASE_URL}commodore-amiga-firmware",
		category = FileCategory.ROMS
	)

	// Archive.org's collection ids are not perfectly aligned with the visible
	// letter titles, so the known mismatches are kept here explicitly.
	private val adfIdentifierOverrides = mapOf(
		'F' to "commodore-amiga-games-adf-f_20220210",
		'L' to "commodore-amiga-games-adf-l_202202",
		'T' to "commodore-amiga-games-adf-r_202301",
		'Y' to "commodore-amiga-games-adf-v_202301"
	)

	val adfCollections: List<ArchiveCollection> = ('A'..'Z').map { letter ->
		val identifier = adfIdentifierOverrides[letter]
			?: "commodore-amiga-games-adf-${letter.lowercaseChar()}"
		ArchiveCollection(
			id = "adf-$letter",
			fallbackTitle = "Commodore Amiga - Games - [ADF] ($letter)",
			identifier = identifier,
			detailUrl = "$DETAILS_BASE_URL$identifier",
			category = FileCategory.FLOPPIES,
			letter = letter
		)
	}

	fun metadataUrl(collection: ArchiveCollection): String {
		return "${DOWNLOAD_BASE_URL}${collection.identifier}"
			.replace("/download/", "/metadata/")
	}

	fun fileDownloadUrl(collection: ArchiveCollection, fileName: String): String {
		val encodedName = Uri.encode(fileName, "/")
		return "$DOWNLOAD_BASE_URL${collection.identifier}/$encodedName"
	}
}
