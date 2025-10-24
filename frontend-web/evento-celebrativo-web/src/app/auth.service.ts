import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private apiUrl = 'http://localhost:8080/public';


  constructor(private http: HttpClient) {}

  login(credentials: any): Observable<any> {

    const body = {
      username: credentials.phone,
      password: credentials.password
    };
    
    return this.http.post(`${this.apiUrl}/login`, body);

  }
  
}
