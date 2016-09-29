/*
 * Copyright 2016 Andy Bao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.nulldev.ts.api.http.download

import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.source.SourceManager
import spark.Request
import spark.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.ts.api.http.TachiWebRoute
import xyz.nulldev.ts.util.ChapterUtils
import xyz.nulldev.ts.util.LeniantParser

/**
 * Download a chapter
 */
class DownloadChapterRoute : TachiWebRoute() {

    private val sourceManager: SourceManager = Injekt.get()
    private val downloadManager: DownloadManager = Injekt.get()

    @Throws(Exception::class)
    override fun handleReq(request: Request, response: Response): Any {
        val mangaId = LeniantParser.parseLong(request.params(":mangaId"))
        val chapterId = LeniantParser.parseLong(request.params(":chapterId"))
        if (mangaId == null) {
            return error("MangaID must be specified!")
        } else if (chapterId == null) {
            return error("ChapterID must be specified!")
        }
        val manga = library.getManga(mangaId) ?: return error("The specified manga does not exist!")
        val source = sourceManager.get(manga.source) ?: throw IllegalArgumentException()
        val chapter = library.getChapter(chapterId) ?: return error("The specified chapter does not exist!")
        val delete = "true".equals(request.queryParams("delete"), ignoreCase = true)
        val activeDownload = ChapterUtils.getDownload(downloadManager, chapter)
        if (activeDownload != null) {
            if (delete) {
                return error("This chapter is currently being downloaded!")
            } else {
                return error("This chapter is already being downloaded!")
            }
        }
        val isChapterDownloded = downloadManager.isChapterDownloaded(source, manga, chapter)
        if (!delete && isChapterDownloded) {
            return error("This chapter is already downloaded!")
        }
        if (delete && !isChapterDownloded) {
            return error("This chapter is not downloaded!")
        }
        if (delete) {
            val wasRunning = downloadManager.isRunning
            if (wasRunning) {
                downloadManager.destroySubscriptions()
            }
            downloadManager.deleteChapter(source, manga, chapter)
            if (wasRunning) {
                downloadManager.startDownloads()
            }
        } else {
            downloadManager.downloadChapters(manga, listOf(chapter))
            downloadManager.startDownloads()
        }
        return success()
    }
}
