fun isPrime(n: Int, i: Int = 2): Boolean {
    // the first prime number would be 2
    if(n < 2) return false

    // recursive case here
    while(n != i) {
        if(n % i != 0) {
            return isPrime(n, i + 1)
        }
        return false
    }

    return true
}

fun main() {
    val n = readLine()!!.toInt()
    print(isPrime(n))
}