package gitinternals

import java.io.FileInputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.InflaterInputStream

fun main() {
    // write your code here
    println("Enter .git directory location:")
    val gitDirectory = readln()

    println("Enter git object hash:")
    val hash = readln()
    val folder = hash.slice(0 until 2)
    val rest = hash.substring(2)

    val inputStream = FileInputStream("$gitDirectory/objects/$folder/$rest")
    val inflaterInputStream = InflaterInputStream(inputStream)
    val byteArray = inflaterInputStream.readAllBytes()
    val data = byteArray.toString(Charsets.UTF_8)

    val sections = data.split('\u0000', limit = 2)
    if (sections.size == 2) {
        val sectionParts = sections[0].split(" ")
        val header = sectionParts[0]

        println("*${header.uppercase()}*")

        when (header) {
            "commit" -> {
                println(transformCommitBody(sections[1]))
            }

            "blob" -> {
                println(sections[1])
            }

            "tree" -> {
                println(sections[1])
            }

            else -> println("unknown header")
        }

    } else {
        println("Unexpected data format")
    }
}

fun transformCommitBody(body: String): String {
    val lines = body.split("\n").toMutableList()
    var commitMessageIndex: Int = 0

    // Check for multiple parents
    val parentLines = lines.filter {it.startsWith("parent")}
    if(parentLines.size > 1) {
        val combinedStr = "${parentLines[0]} | ${parentLines[1].split(" ")[1]}"
        val index = lines.indexOfFirst { it.startsWith("parent") }
        lines[index] = combinedStr
        lines.removeAt(index + 1)
    }

    // Sanitize each line
    for (index in lines.indices) {
        val parts = lines[index].split(" ", limit = 2)
        val title = parts[0]

        lines[index] = when (title) {
            "author" -> "$title: ${sanitizeLine("author", parts[1])}"
            "committer" -> "$title: ${sanitizeLine("committer", parts[1])}"
            "parent" -> "${title + "s"}: ${parts[1]}"
            "tree" -> "$title: ${parts[1]}"
            else -> {
                commitMessageIndex = index
                lines[index]
                break
            }
        }
    }

    // Insert a line
    lines[commitMessageIndex] = "commit message:"

    return lines.joinToString("\n")
}

fun sanitizeLine(type: String, line: String): String {
    val parts = line.split(" ")
    val name = parts[0]
    val email = parts[1].replace("<", "").replace(">", "")
    val unixSeconds = parts[2]
    val timeZone = parts[3]
    val lineType = if (type == "author") "original" else "commit"

    // Unix seconds to human-readable format
    val instant = Instant.ofEpochSecond(unixSeconds.toLong())
    val zoneId = ZoneId.ofOffset("UTC", ZoneOffset.of(timeZone))
    val localTimeDate = LocalDateTime.ofInstant(instant, zoneId)
    val formatter = DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss")
    val formattedTime = localTimeDate.format(formatter)

    // Format time zone
    val formattedTimeZone = "${timeZone.dropLast(2)}:${timeZone.takeLast(2)}"

    return "$name $email $lineType timestamp: $formattedTime $formattedTimeZone"
}
