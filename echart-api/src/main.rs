use axum::{
    routing::get,
    Router,
};
use tower_http::cors::CorsLayer;

mod models;
mod handlers;

#[tokio::main]
async fn main() {
    let app = Router::new()
        .route("/api/power/data", get(handlers::get_power_data))
        .route("/api/mow/data", get(handlers::get_mow_data))
        .layer(CorsLayer::permissive());

    let listener = tokio::net::TcpListener::bind("127.0.0.1:3000")
        .await
        .unwrap();

    println!("Server running on http://127.0.0.1:3000");
    axum::serve(listener, app).await.unwrap();
}
