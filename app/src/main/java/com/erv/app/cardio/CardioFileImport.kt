package com.erv.app.cardio

import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt
import org.w3c.dom.Element
import org.w3c.dom.Node

enum class CardioFileFormat { GPX, TCX, FIT }

/** One ride, run or row read from a watch or app export, before it is turned into a [CardioSession]. */
data class CardioFileActivity(
    val format: CardioFileFormat,
    /** The file's own sport words, lower case ("cycling", "running", "indoor_cycling"), when it names one. */
    val sport: String?,
    val name: String?,
    val startEpochSeconds: Long,
    val endEpochSeconds: Long,
    /** Time moving (FIT timer time, TCX lap time) when the file says; else start to end. */
    val movingSeconds: Int?,
    /** The file's own total distance; null means "work it out from the route". */
    val distanceMeters: Double?,
    val points: List<CardioGpsPoint>,
    val heartRate: List<CardioHrSample>,
) {
    val elapsedSeconds: Int get() = (endEpochSeconds - startEpochSeconds).toInt().coerceAtLeast(0)

    /** Distance from the file, else the length of the route. */
    val resolvedDistanceMeters: Double?
        get() = distanceMeters?.takeIf { it > 0 }
            ?: points.takeIf { it.size >= 2 }?.let { CardioGpsMath.pathLengthMeters(it) }?.takeIf { it > 0 }
}

sealed class CardioFileParse {
    data class Parsed(val activity: CardioFileActivity) : CardioFileParse()
    data class Failed(val message: String) : CardioFileParse()
}

/**
 * GPX, TCX and FIT files from watches, Strava, Garmin and the like, in and out. Everything is
 * read on the phone; no accounts or servers.
 */
object CardioFileImport {

    /** A session that started within this many seconds of the file's start is the same one. */
    private const val DUPLICATE_WINDOW_SECONDS = 120L

    fun detect(bytes: ByteArray, fileName: String? = null): CardioFileFormat? {
        if (bytes.size >= 12 && bytes[8] == '.'.code.toByte() && bytes[9] == 'F'.code.toByte() &&
            bytes[10] == 'I'.code.toByte() && bytes[11] == 'T'.code.toByte()
        ) {
            return CardioFileFormat.FIT
        }
        val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.UTF_8)
        return when {
            head.contains("<gpx", ignoreCase = true) -> CardioFileFormat.GPX
            head.contains("TrainingCenterDatabase") -> CardioFileFormat.TCX
            else -> when (fileName?.substringAfterLast('.', "")?.lowercase(Locale.US)) {
                "gpx" -> CardioFileFormat.GPX
                "tcx" -> CardioFileFormat.TCX
                "fit" -> CardioFileFormat.FIT
                else -> null
            }
        }
    }

    fun parse(bytes: ByteArray, fileName: String? = null): CardioFileParse {
        val format = detect(bytes, fileName)
            ?: return CardioFileParse.Failed("This isn't a GPX, TCX or FIT file.")
        val activity = try {
            when (format) {
                CardioFileFormat.GPX -> parseGpx(bytes)
                CardioFileFormat.TCX -> parseTcx(bytes)
                CardioFileFormat.FIT -> FitDecoder(bytes).decode()
            }
        } catch (e: Exception) {
            return CardioFileParse.Failed("Couldn't read this ${format.name} file: ${e.message ?: e.javaClass.simpleName}")
        }
        return activity?.let { CardioFileParse.Parsed(it) }
            ?: CardioFileParse.Failed("This ${format.name} file has no timed activity in it.")
    }

    /** The built-in activity the file's sport words point to; null when they don't say. */
    fun guessActivity(activity: CardioFileActivity): CardioBuiltinActivity? {
        val words = listOfNotNull(activity.sport, activity.name).joinToString(" ").lowercase(Locale.US)
        return when {
            words.contains("indoor_cycling") || words.contains("indoor cycling") || words.contains("virtual") ||
                words.contains("spin") || words.contains("trainer") -> CardioBuiltinActivity.STATIONARY_BIKE
            words.contains("cycl") || words.contains("bik") || words.contains("ride") -> CardioBuiltinActivity.BIKE
            words.contains("ruck") -> CardioBuiltinActivity.RUCK
            words.contains("hik") -> CardioBuiltinActivity.HIKE
            words.contains("run") || words.contains("treadmill") -> CardioBuiltinActivity.RUN
            words.contains("walk") -> CardioBuiltinActivity.WALK
            words.contains("row") -> CardioBuiltinActivity.ROWING
            words.contains("swim") -> CardioBuiltinActivity.SWIM
            words.contains("ellip") -> CardioBuiltinActivity.ELLIPTICAL
            else -> null
        }
    }

    /** True when the file's start is on a treadmill or the file has no route. */
    fun isIndoor(activity: CardioFileActivity): Boolean {
        val words = activity.sport.orEmpty()
        return words.contains("treadmill") || words.contains("indoor") || activity.points.size < 2
    }

    fun localDate(activity: CardioFileActivity, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochSecond(activity.startEpochSeconds).atZone(zone).toLocalDate()

    /** A session already in the log that started within two minutes of this file. */
    fun findDuplicate(state: CardioLibraryState, activity: CardioFileActivity): CardioSession? =
        state.logs.asSequence().flatMap { it.sessions.asSequence() }.firstOrNull { session ->
            val start = session.startEpochSeconds ?: return@firstOrNull false
            kotlin.math.abs(start - activity.startEpochSeconds) <= DUPLICATE_WINDOW_SECONDS
        }

    fun toSession(activity: CardioFileActivity, snapshot: CardioActivitySnapshot): CardioSession {
        val seconds = activity.movingSeconds?.takeIf { it > 0 } ?: activity.elapsedSeconds
        val bpm = activity.heartRate.map { it.bpm }.filter { it > 0 }
        val heartRate = if (bpm.isNotEmpty()) {
            CardioHrScaffolding(
                avgBpm = bpm.average().roundToInt(),
                maxBpm = bpm.max(),
                minBpm = bpm.min(),
                samples = activity.heartRate,
            )
        } else {
            null
        }
        val route = activity.points.takeIf { it.size >= 2 }
        val elevation = route?.let { CardioGpsElevation.computeGainLossMeters(it) }
        val treadmill = snapshot.builtin in setOf(CardioBuiltinActivity.RUN, CardioBuiltinActivity.WALK) &&
            activity.sport.orEmpty().contains("treadmill")
        return CardioSession(
            activity = snapshot,
            modality = if (treadmill) CardioModality.INDOOR_TREADMILL else CardioModality.OUTDOOR,
            durationMinutes = ((seconds + 30) / 60).coerceAtLeast(1),
            distanceMeters = activity.resolvedDistanceMeters,
            source = CardioSessionSource.IMPORTED,
            startEpochSeconds = activity.startEpochSeconds,
            endEpochSeconds = activity.endEpochSeconds,
            heartRate = heartRate,
            gpsTrack = route?.let { CardioGpsTrack(points = it, source = "import_${activity.format.name.lowercase(Locale.US)}") },
            elevationGainMeters = elevation?.first,
            elevationLossMeters = elevation?.second,
        )
    }

    /** The session's route as a GPX 1.1 file, with heart rate on each point where it was recorded. */
    fun toGpx(session: CardioSession): String? {
        val points = session.gpsTrack?.points?.takeIf { it.isNotEmpty() } ?: return null
        val samples = session.heartRate?.samples.orEmpty().sortedBy { it.epochSeconds }
        val time = DateTimeFormatter.ISO_INSTANT
        val type = when (session.activity.builtin) {
            CardioBuiltinActivity.BIKE, CardioBuiltinActivity.STATIONARY_BIKE -> "cycling"
            CardioBuiltinActivity.RUN, CardioBuiltinActivity.SPRINT -> "running"
            CardioBuiltinActivity.HIKE -> "hiking"
            CardioBuiltinActivity.ROWING -> "rowing"
            CardioBuiltinActivity.SWIM -> "swimming"
            else -> "walking"
        }
        var hrIndex = 0
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<gpx version="1.1" creator="ERV" xmlns="http://www.topografix.com/GPX/1/1" """)
            append("""xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">""").append('\n')
            append("  <metadata><time>").append(time.format(Instant.ofEpochSecond(points.first().epochSeconds)))
            append("</time></metadata>\n")
            append("  <trk>\n    <name>").append(xmlEscape(session.activity.displayLabel)).append("</name>\n")
            append("    <type>").append(type).append("</type>\n    <trkseg>\n")
            for (p in points) {
                append("      <trkpt lat=\"").append(p.lat).append("\" lon=\"").append(p.lon).append("\">")
                p.altitudeMeters?.let { append("<ele>").append(String.format(Locale.US, "%.1f", it)).append("</ele>") }
                append("<time>").append(time.format(Instant.ofEpochSecond(p.epochSeconds))).append("</time>")
                // The last heart rate reading at or before this point, if within 10 s.
                while (hrIndex + 1 < samples.size && samples[hrIndex + 1].epochSeconds <= p.epochSeconds) hrIndex++
                samples.getOrNull(hrIndex)?.takeIf { kotlin.math.abs(it.epochSeconds - p.epochSeconds) <= 10 }?.let {
                    append("<extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>").append(it.bpm)
                    append("</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions>")
                }
                append("</trkpt>\n")
            }
            append("    </trkseg>\n  </trk>\n</gpx>\n")
        }
    }

    private fun xmlEscape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    // ---- XML (GPX and TCX) ----

    private fun readXml(bytes: ByteArray): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // No DOCTYPEs: an export never needs one, and refusing them shuts out external entities.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
    }

    private fun Element.all(localName: String): List<Element> {
        val nodes = getElementsByTagNameNS("*", localName)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun Element.child(localName: String): Element? {
        var node: Node? = firstChild
        while (node != null) {
            if (node is Element && (node.localName ?: node.nodeName.substringAfter(':')) == localName) return node
            node = node.nextSibling
        }
        return null
    }

    private fun Element.childText(localName: String): String? = child(localName)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    internal fun parseTime(text: String?): Long? {
        val t = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        runCatching { return OffsetDateTime.parse(t).toEpochSecond() }
        runCatching { return Instant.parse(t).epochSecond }
        runCatching { return LocalDateTime.parse(t).toEpochSecond(ZoneOffset.UTC) }
        return null
    }

    private fun parseGpx(bytes: ByteArray): CardioFileActivity? {
        val root = readXml(bytes)
        val points = mutableListOf<CardioGpsPoint>()
        val heartRate = mutableListOf<CardioHrSample>()
        for (pt in root.all("trkpt")) {
            val lat = pt.getAttribute("lat").toDoubleOrNull() ?: continue
            val lon = pt.getAttribute("lon").toDoubleOrNull() ?: continue
            val at = parseTime(pt.childText("time")) ?: continue
            points += CardioGpsPoint(lat = lat, lon = lon, epochSeconds = at, altitudeMeters = pt.childText("ele")?.toDoubleOrNull())
            val bpm = (pt.all("hr").firstOrNull() ?: pt.all("heartrate").firstOrNull())?.textContent?.trim()?.toIntOrNull()
            if (bpm != null && bpm > 0) heartRate += CardioHrSample(at, bpm)
        }
        if (points.isEmpty()) return null
        val track = root.all("trk").firstOrNull()
        val start = points.minOf { it.epochSeconds }
        val end = points.maxOf { it.epochSeconds }
        if (end <= start) return null
        return CardioFileActivity(
            format = CardioFileFormat.GPX,
            sport = track?.childText("type")?.let { gpxType(it) },
            name = track?.childText("name") ?: root.all("metadata").firstOrNull()?.childText("name"),
            startEpochSeconds = start,
            endEpochSeconds = end,
            movingSeconds = null,
            distanceMeters = null,
            points = points.sortedBy { it.epochSeconds },
            heartRate = heartRate.sortedBy { it.epochSeconds },
        )
    }

    /** Strava writes numbers for the GPX type; others write words. */
    private fun gpxType(type: String): String = when (type.trim()) {
        "1" -> "cycling"
        "4" -> "hiking"
        "9" -> "running"
        "10" -> "walking"
        else -> type.trim().lowercase(Locale.US)
    }

    private fun parseTcx(bytes: ByteArray): CardioFileActivity? {
        val root = readXml(bytes)
        val activity = root.all("Activity").firstOrNull() ?: return null
        val points = mutableListOf<CardioGpsPoint>()
        val heartRate = mutableListOf<CardioHrSample>()
        var lastDistance: Double? = null
        var firstTime: Long? = null
        var lastTime: Long? = null
        for (tp in activity.all("Trackpoint")) {
            val at = parseTime(tp.childText("Time")) ?: continue
            if (firstTime == null || at < firstTime) firstTime = at
            if (lastTime == null || at > lastTime) lastTime = at
            tp.child("Position")?.let { pos ->
                val lat = pos.childText("LatitudeDegrees")?.toDoubleOrNull()
                val lon = pos.childText("LongitudeDegrees")?.toDoubleOrNull()
                if (lat != null && lon != null) {
                    points += CardioGpsPoint(lat, lon, at, tp.childText("AltitudeMeters")?.toDoubleOrNull())
                }
            }
            tp.child("HeartRateBpm")?.childText("Value")?.toIntOrNull()?.takeIf { it > 0 }?.let {
                heartRate += CardioHrSample(at, it)
            }
            tp.childText("DistanceMeters")?.toDoubleOrNull()?.let { lastDistance = it }
        }
        val laps = activity.all("Lap")
        val lapSeconds = laps.mapNotNull { it.childText("TotalTimeSeconds")?.toDoubleOrNull() }.sum()
        val lapMeters = laps.mapNotNull { it.childText("DistanceMeters")?.toDoubleOrNull() }.sum()
        val start = parseTime(activity.childText("Id"))
            ?: laps.firstOrNull()?.getAttribute("StartTime")?.let { parseTime(it) }
            ?: firstTime
            ?: return null
        val end = lastTime?.takeIf { it > start } ?: (start + lapSeconds.roundToInt()).takeIf { lapSeconds > 0 } ?: return null
        return CardioFileActivity(
            format = CardioFileFormat.TCX,
            sport = activity.getAttribute("Sport").takeIf { it.isNotBlank() }?.lowercase(Locale.US),
            name = activity.childText("Notes"),
            startEpochSeconds = start,
            endEpochSeconds = end,
            movingSeconds = lapSeconds.takeIf { it > 0 }?.roundToInt(),
            distanceMeters = lapMeters.takeIf { it > 0 } ?: lastDistance,
            points = points.sortedBy { it.epochSeconds },
            heartRate = heartRate.sortedBy { it.epochSeconds },
        )
    }
}

/**
 * A small reader for Garmin FIT activity files: the session summary (sport, start, times,
 * distance) and each record (time, position, altitude, heart rate). Every other message is skipped.
 */
internal class FitDecoder(private val bytes: ByteArray) {

    private class FieldDef(val number: Int, val size: Int, val baseType: Int)
    private class MessageDef(val global: Int, val bigEndian: Boolean, val fields: List<FieldDef>, val devBytes: Int)

    private val defs = arrayOfNulls<MessageDef>(16)
    private var pos = 0
    private var lastTimestamp = 0L

    private val points = mutableListOf<CardioGpsPoint>()
    private val heartRate = mutableListOf<CardioHrSample>()
    private var lastRecordDistance: Double? = null
    private var firstRecordTime: Long? = null
    private var lastRecordTime: Long? = null
    private var sport: Int? = null
    private var subSport: Int? = null
    private var sessionStart: Long? = null
    private var elapsedSeconds: Double? = null
    private var timerSeconds: Double? = null
    private var sessionDistance: Double? = null

    fun decode(): CardioFileActivity? {
        require(bytes.size >= 12) { "file too short" }
        val headerSize = bytes[0].toInt() and 0xFF
        val dataSize = u32le(4).toInt()
        require(headerSize >= 12 && headerSize <= bytes.size) { "bad header" }
        pos = headerSize
        val end = minOf(bytes.size, headerSize + dataSize).let { if (dataSize <= 0) bytes.size - 2 else it }
        while (pos < end) {
            val header = bytes[pos++].toInt() and 0xFF
            when {
                header and 0x80 != 0 -> {
                    // Compressed timestamp header: a data message with a 5-bit time offset.
                    val local = (header shr 5) and 0x03
                    val offset = header and 0x1F
                    var ts = (lastTimestamp and 0x1FL.inv()) + offset
                    if (offset < (lastTimestamp and 0x1F)) ts += 0x20
                    lastTimestamp = ts
                    readData(defs[local] ?: error("no definition for message $local"), ts)
                }
                header and 0x40 != 0 -> readDefinition(header and 0x0F, header and 0x20 != 0)
                else -> readData(defs[header and 0x0F] ?: error("no definition for message ${header and 0x0F}"), null)
            }
        }
        val start = sessionStart?.let { it + FIT_EPOCH } ?: firstRecordTime ?: return null
        val endTime = lastRecordTime?.takeIf { it > start }
            ?: elapsedSeconds?.let { start + it.roundToInt() }?.takeIf { it > start }
            ?: return null
        return CardioFileActivity(
            format = CardioFileFormat.FIT,
            sport = sportWords(sport, subSport),
            name = null,
            startEpochSeconds = start,
            endEpochSeconds = endTime,
            movingSeconds = timerSeconds?.roundToInt(),
            distanceMeters = sessionDistance ?: lastRecordDistance,
            points = points,
            heartRate = heartRate,
        )
    }

    private fun readDefinition(local: Int, developer: Boolean) {
        pos += 1 // reserved
        val bigEndian = byte(pos++) == 1
        val global = if (bigEndian) u16be(pos) else u16le(pos)
        pos += 2
        val count = byte(pos++)
        val fields = (0 until count).map {
            FieldDef(byte(pos), byte(pos + 1), byte(pos + 2)).also { pos += 3 }
        }
        var devBytes = 0
        if (developer) {
            val devCount = byte(pos++)
            repeat(devCount) {
                devBytes += byte(pos + 1)
                pos += 3
            }
        }
        defs[local] = MessageDef(global, bigEndian, fields, devBytes)
    }

    private fun readData(def: MessageDef, compressedTimestamp: Long?) {
        val values = HashMap<Int, Long>()
        for (field in def.fields) {
            val v = readValue(field, def.bigEndian)
            pos += field.size
            if (v != null) values[field.number] = v
        }
        pos += def.devBytes
        values[TIMESTAMP]?.let { lastTimestamp = it }
        val ts = values[TIMESTAMP] ?: compressedTimestamp
        when (def.global) {
            MSG_RECORD -> record(values, ts)
            MSG_SESSION -> session(values)
        }
    }

    private fun record(values: Map<Int, Long>, ts: Long?) {
        val at = (ts ?: return) + FIT_EPOCH
        if (firstRecordTime == null || at < firstRecordTime!!) firstRecordTime = at
        if (lastRecordTime == null || at > lastRecordTime!!) lastRecordTime = at
        val lat = values[0]?.toInt()?.let { it * SEMICIRCLE_TO_DEGREES }
        val lon = values[1]?.toInt()?.let { it * SEMICIRCLE_TO_DEGREES }
        val altitude = (values[78] ?: values[2])?.let { it / 5.0 - 500.0 }
        if (lat != null && lon != null) points += CardioGpsPoint(lat, lon, at, altitude)
        values[3]?.toInt()?.takeIf { it > 0 }?.let { heartRate += CardioHrSample(at, it) }
        values[5]?.let { lastRecordDistance = it / 100.0 }
    }

    private fun session(values: Map<Int, Long>) {
        // A multisport file has several sessions; the first start and the totals of all are kept.
        values[2]?.let { if (sessionStart == null) sessionStart = it }
        values[5]?.let { if (sport == null) sport = it.toInt() }
        values[6]?.let { if (subSport == null) subSport = it.toInt() }
        values[7]?.let { elapsedSeconds = (elapsedSeconds ?: 0.0) + it / 1000.0 }
        values[8]?.let { timerSeconds = (timerSeconds ?: 0.0) + it / 1000.0 }
        values[9]?.let { sessionDistance = (sessionDistance ?: 0.0) + it / 100.0 }
    }

    /** An unsigned value of 1, 2 or 4 bytes, or null when it is the type's "invalid" value or an array. */
    private fun readValue(field: FieldDef, bigEndian: Boolean): Long? {
        if (pos + field.size > bytes.size) error("message runs past the end")
        val signed = field.baseType and 0x1F in setOf(0x01, 0x03, 0x05)
        val raw = when (field.size) {
            1 -> byte(pos).toLong()
            2 -> (if (bigEndian) u16be(pos) else u16le(pos)).toLong()
            4 -> if (bigEndian) u32be(pos) else u32le(pos)
            else -> return null
        }
        val invalid = when (field.size) {
            1 -> if (signed) 0x7FL else 0xFFL
            2 -> if (signed) 0x7FFFL else 0xFFFFL
            else -> if (signed) 0x7FFFFFFFL else 0xFFFFFFFFL
        }
        return raw.takeIf { it != invalid }
    }

    private fun byte(i: Int): Int = bytes[i].toInt() and 0xFF
    private fun u16le(i: Int): Int = byte(i) or (byte(i + 1) shl 8)
    private fun u16be(i: Int): Int = (byte(i) shl 8) or byte(i + 1)
    private fun u32le(i: Int): Long = (u16le(i).toLong()) or (u16le(i + 2).toLong() shl 16)
    private fun u32be(i: Int): Long = (u16be(i).toLong() shl 16) or u16be(i + 2).toLong()

    private fun sportWords(sport: Int?, subSport: Int?): String? {
        val sub = when (subSport) {
            1 -> "treadmill"
            6 -> "indoor_cycling"
            14 -> "indoor_rowing"
            15 -> "elliptical"
            58 -> "virtual"
            else -> null
        }
        val main = when (sport) {
            1 -> "running"
            2 -> "cycling"
            5 -> "swimming"
            11 -> "walking"
            15 -> "rowing"
            17 -> "hiking"
            4 -> "fitness_equipment"
            else -> null
        }
        return listOfNotNull(sub, main).joinToString(" ").takeIf { it.isNotEmpty() }
    }

    companion object {
        /** Seconds from the Unix epoch to the FIT epoch, 1989-12-31 00:00 UTC. */
        const val FIT_EPOCH = 631_065_600L
        const val MSG_SESSION = 18
        const val MSG_RECORD = 20
        const val TIMESTAMP = 253
        const val SEMICIRCLE_TO_DEGREES = 180.0 / 2147483648.0
    }
}
