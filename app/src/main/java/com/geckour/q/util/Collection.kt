package com.geckour.q.util

fun <T> MutableList<T>.swap(from: Int, to: Int) {
    val tmp = this[to]
    this[to] = this[from]
    this[from] = tmp
}

fun <T> MutableList<T>.swapped(from: Int, to: Int): MutableList<T> = this.apply { swap(from, to) }

fun <T> MutableList<T>.move(from: Int, to: Int) {
    val tmp = this[from]
    this.removeAt(from)
    this.add(to, tmp)
}