package com.example.userdirectory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

// ---------------------------------------------------------
// ROOM DATABASE
// ---------------------------------------------------------
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val email: String,
    val phone: String
)

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY name ASC")
    fun getUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE name LIKE '%' || :query || '%' OR email LIKE '%' || :query || '%'")
    fun searchUsers(query: String): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(users: List<UserEntity>)
}

@Database(entities = [UserEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "users.db"
                ).build().also { INSTANCE = it }
            }
    }
}

// ---------------------------------------------------------
// RETROFIT
// ---------------------------------------------------------
data class UserNetwork(
    val id: Int,
    val name: String,
    val email: String,
    val phone: String
)

interface ApiService {
    @GET("users")
    suspend fun getUsers(): List<UserNetwork>
}

object RetrofitClient {
    fun create(): ApiService {
        return Retrofit.Builder()
            .baseUrl("https://jsonplaceholder.typicode.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

// ---------------------------------------------------------
// REPOSITORY
// ---------------------------------------------------------
class UserRepository(
    private val api: ApiService,
    private val dao: UserDao
) {
    fun getUsers(query: String): Flow<List<UserEntity>> =
        if (query.isBlank()) dao.getUsers()
        else dao.searchUsers(query)

    suspend fun refreshUsers() {
        try {
            val apiUsers = api.getUsers()
            val entities = apiUsers.map {
                UserEntity(it.id, it.name, it.email, it.phone)
            }
            dao.insertAll(entities)
        } catch (_: Exception) {
            // Offline → ignore errors
        }
    }
}

// ---------------------------------------------------------
// VIEWMODEL
// ---------------------------------------------------------
class UserViewModel(private val repo: UserRepository) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val users: StateFlow<List<UserEntity>> =
        searchQuery.debounce(200)
            .flatMapLatest { repo.getUsers(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearch(q: String) {
        searchQuery.value = q
    }

    init {
        viewModelScope.launch {
            repo.refreshUsers()
        }
    }
}

// ---------------------------------------------------------
// UI
// ---------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(vm: UserViewModel = viewModel()) {
    val users by vm.users.collectAsState()
    var search by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = {
                search = it
                vm.setSearch(it)
            },
            label = { Text("Search name or email") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(users) { user ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = user.name, style = MaterialTheme.typography.titleMedium)
                        Text(text = user.email)
                        Text(text = user.phone)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// MAIN ACTIVITY (single-file app entry point)
// ---------------------------------------------------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val api = RetrofitClient.create()
        val db = AppDatabase.getInstance(applicationContext)
        val repo = UserRepository(api, db.userDao())

        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return UserViewModel(repo) as T
            }
        }

        setContent {
            val vm: UserViewModel = viewModel(factory = factory)
            UsersScreen(vm)
        }
    }
}
