package gitinternals

import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.InflaterInputStream
import kotlin.collections.copyOfRange

fun main() {
    println("Enter .git directory location:")
    val gitDirectory = readln()

    println("Enter command:")
    val command = readln()

    when (command) {
        "list-branches" -> listBranches(gitDirectory)
        "cat-file" -> runCatFileCommand(gitDirectory)
        "log" -> runLogCommand(gitDirectory)
        "commit-tree" -> runCommitTreeCommand(gitDirectory)
    }
}

// Functions to run the commands
fun listBranches (gitDirectory: String) {
    val headBranch = getHeadBranch(gitDirectory)
    val branches = getBranches(gitDirectory)

    if(branches != null) {
        val sortedBranches = branches.map {it.name}.sorted()
        sortedBranches.forEach {
            println(if(it == headBranch) "* $it" else "  $it")
        }
    } else {
        println("No branches found")
    }

}

fun runCatFileCommand (gitDirectory: String) {
    println("Enter git object hash:")
    val hash = readln()

    val decompressedData = decompressFile(gitDirectory, hash)

    val dataStr = decompressedData.toString(Charsets.UTF_8)

    val sections = dataStr.split('\u0000', limit = 2)
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
                // In order to convert the SHA-1, you need the original byteArray
                println(Tree(decompressedData.copyOfRange(sections[0].length + 1, decompressedData.size)).toString())
            }

            else -> println("unknown header")
        }

    } else {
        println("Unexpected data format")
    }
}

fun runLogCommand (gitDirectory: String) {
    println("Enter branch name:")
    val branchName = readln()

    val branches = getBranches(gitDirectory)
    val branch = branches?.find {it.name == branchName}
    val file = File("$gitDirectory/refs/heads/${branch?.name}")

    if(file.exists()) {
        // Branch file points to a commit object
        val commitSha = listOf(file.readText().replace("\n", ""))
        printLog(gitDirectory, commitSha)
    }
}

fun runCommitTreeCommand (gitDirectory: String) {
    println("Enter commit-hash:")
    val hash = readln()

    // commit object
    val decompressedData = decompressFile(gitDirectory, hash)
    val dataStr = decompressedData.toString(Charsets.UTF_8)
    val treeLine = dataStr.split("\u0000", limit = 2)[1].split("\n").find {it.startsWith("tree")}!!

    // tree object
    fun printTreeList (hash: String, folderName: String = "") {
        val treeDecompressedData = decompressFile(gitDirectory, hash)
        val treeDataStr = treeDecompressedData.toString(Charsets.UTF_8)
        val sections = treeDataStr.split("\u0000", limit = 2)
        val treeList = Tree(treeDecompressedData.copyOfRange(sections[0].length + 1, treeDecompressedData.size)).convertedData

        for(item in treeList) {
            val name =  item.split(" ")[2]
            val isFile = name.contains(".")

            if(folderName.isNotBlank()) print("$folderName/") else print("")
            if(isFile) {
                println(name)
            } else {
                printTreeList(hash = item.split(" ")[1], folderName = name)
            }
        }
    }

    printTreeList(treeLine.split(" ", limit = 2)[1])

}

// Util functions
fun getHeadBranch (gitDirectory: String): String {
    val headFile = File("$gitDirectory/HEAD")

    if(headFile.exists()) {
        val content = headFile.readText()
        val fileSeparator = File.separator

        // There is a \n character at the end
        return content.split(fileSeparator).last().replace("\n", "")
    } else {
        println("HEAD file doesn't exist")
        return ""
    }
}

fun getBranches (gitDirectory: String): Array<File>? {
    val headsDir = File("$gitDirectory/refs/heads")
    if(headsDir.exists() && headsDir.isDirectory()) {
        return headsDir.listFiles()
    }
        println("No branches found")
        return null

}

fun decompressFile (gitDirectory: String, hash: String): ByteArray {
    val folder = hash.slice(0 until 2)
    val rest = hash.substring(2)

    val inputStream = FileInputStream("$gitDirectory/objects/$folder/$rest")
    val inflaterInputStream = InflaterInputStream(inputStream)
    return inflaterInputStream.readAllBytes()
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

// Tried to use class instead of a function
class Tree (private val originalData: ByteArray) {
    var convertedData: MutableList<String> = mutableListOf()

    init {
        var inputStr = originalData.toString(Charsets.ISO_8859_1)

        // (?s) makes . to match any character, including \n
        val itemRegex = """(?s)(?<permission>\d+)\s(?<fileName>[^\u0000]+)\u0000(?<sha>.{20})""".toRegex()

        var currentIndex = 0
        while(currentIndex < inputStr.length) {
            val match = itemRegex.find(inputStr, currentIndex)
            if(match != null) {
                val permissionMetadata = match.groups["permission"]?.value
                val fileName = match.groups["fileName"]?.value
                val shaStartIndex = match.groups["sha"]!!.range.first
                val shaEndIndex = match.groups["sha"]!!.range.last + 1

                // Instead of formatting a string, we need the original byteArray to correctly convert
                val sha = originalData.copyOfRange(shaStartIndex, shaEndIndex).joinToString("") {"%02x".format(it)}
                convertedData.add("$permissionMetadata $sha $fileName")
                currentIndex = shaEndIndex
            } else {
                break
            }
        }

    }

    override fun toString(): String {
        return convertedData.joinToString("\n")
    }

    fun getListOfNames(): List<String> {
        return convertedData.map { it.split(" ").last() }
    }
}

fun printLog (gitDirectory: String, shas: List<String>) {
    for (i in shas.indices) {
        val isMergedCommit = shas.size == 2 && i == 0

        val decompressedData = decompressFile(gitDirectory, shas[i])
        val dataStr = decompressedData.toString(Charsets.UTF_8)

        val lines = dataStr.split("\n").toMutableList()

        // Get the index of the committer
        val committerLineIndex = lines.indexOfFirst {it.startsWith("committer")}

        // Sanitize the committer line without "committer"
        val committerLine = sanitizeLine("committer", lines[committerLineIndex].split(" ", limit = 2)[1])
        val commitBody = lines.slice(committerLineIndex + 2 until lines.size)

        // Print commit log
        println("Commit: ${shas[i]}${if(isMergedCommit) " (merged)" else ""}")
        println(committerLine)
        for( line in commitBody) {
            println(line)
        }
        // Check if there is parent commit
        if(!isMergedCommit) {
            // Reverse the order so merged commit is logged first
            val parentShas = lines.filter {it.startsWith("parent")}.map {it.split(" ", limit = 2)[1]}.reversed()

            // If there are any parents, run the function recursively.
            if (parentShas.isNotEmpty()) {
                return printLog(gitDirectory, parentShas)
            }
        }

    }

}