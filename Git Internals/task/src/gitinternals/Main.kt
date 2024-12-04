package gitinternals
import java.io.File
import java.io.FileInputStream;
import java.util.zip.InflaterInputStream;

fun main() {
    // write your code here
    println("Enter git object location:")
    val blob = readln()
    val inputStream = FileInputStream(blob)
    val inflaterInputStream = InflaterInputStream(inputStream)
    val byteArray = inflaterInputStream.readAllBytes()
    val data = byteArray.toString(Charsets.UTF_8)

    val parts = data.split('\u0000', limit = 2)
    if (parts.size == 2) {
        println(parts[0]) // Print the metadata
        println(parts[1]) // Print the actual content
    } else {
        println("Unexpected data format")
    }
}
