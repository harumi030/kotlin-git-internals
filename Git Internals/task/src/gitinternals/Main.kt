package gitinternals
import java.io.FileInputStream;
import java.util.zip.InflaterInputStream;

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

    val header = data.split('\u0000', limit = 2)[0]
    if (header.isNotEmpty()) {
        val parts = header.split(" ")
        println("type:${parts[0]} length:${parts[1]}")
    } else {
        println("Unexpected data format")
    }
}
