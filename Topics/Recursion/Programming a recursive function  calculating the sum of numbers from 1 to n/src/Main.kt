fun sumRecursive(n: Int): Int {
    // base case / terminal condition here
    if (n < 1) return n
    // recursive call here
    return n + sumRecursive(n-1)
}

fun main() {
    val n = readLine()!!.toInt()
    print(sumRecursive(n))
}