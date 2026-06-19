package com.example.todofinalapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ================= 1. DOMAIN LAYER (Бизнес-логика) =================
data class TodoTask(val id: Int = 0, val title: String, val isCompleted: Boolean = false)

// ================= 2. DATA LAYER (Room Database & Реализация) =================
@Entity(tableName = "todo_tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val isCompleted: Boolean
)

fun TaskEntity.toDomain() = TodoTask(id, title, isCompleted)
fun TodoTask.toEntity() = TaskEntity(id, title, isCompleted)

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_tasks ORDER BY id DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Query("UPDATE todo_tasks SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun updateTaskStatus(id: Int, isCompleted: Boolean)

    @Delete
    suspend fun deleteTask(task: TaskEntity)
}

@Database(entities = [TaskEntity::class], version = 1)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    companion object {
        private var INSTANCE: TodoDatabase? = null
        fun getInstance(context: Context): TodoDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, TodoDatabase::class.java, "todo_db")
                    .build().also { INSTANCE = it }
            }
        }
    }
}

// ================= 3. PRESENTATION LAYER (ViewModel) =================
class TodoViewModel(context: Context) : ViewModel() {
    private val dao = TodoDatabase.getInstance(context).todoDao()

    val tasksState: StateFlow<List<TodoTask>> = dao.getAllTasks()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            dao.insertTask(TaskEntity(title = title, isCompleted = false))
        }
    }

    fun toggleTaskStatus(task: TodoTask) {
        viewModelScope.launch {
            dao.updateTaskStatus(task.id, !task.isCompleted)
        }
    }

    fun deleteTask(task: TodoTask) {
        viewModelScope.launch {
            dao.deleteTask(task.toEntity())
        }
    }
}

// ================= 4. UI LAYER (Jetpack Compose) =================
@Composable
fun TodoScreen(viewModel: TodoViewModel) {
    val tasks by viewModel.tasksState.collectAsState()
    var textState by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = textState,
                onValueChange = { textState = it },
                label = { Text("Новая задача") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                viewModel.addTask(textState)
                textState = ""
            }) {
                Text("Добавить")
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.fillMaxSize()) {
            items(tasks, key = { it.id }) { task ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = task.isCompleted,
                            onCheckedChange = { viewModel.toggleTaskStatus(task) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = task.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        IconButton(onClick = { viewModel.deleteTask(task) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить")
                        }
                    }
                }
            }
        }
    }
}

// ================= 5. ENTRY POINT =================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val context = androidx.compose.ui.platform.LocalContext.current
                val viewModel = remember { TodoViewModel(context) }
                TodoScreen(viewModel)
            }
        }
    }
}
