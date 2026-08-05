package com.example.todolock

data class Todo(
    val id: Long,
    var text: String,
    var date: String,   // "yyyy-MM-dd"
    var done: Boolean = false
)
